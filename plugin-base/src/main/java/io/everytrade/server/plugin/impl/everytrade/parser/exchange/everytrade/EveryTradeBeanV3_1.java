package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static lombok.AccessLevel.PRIVATE;

@Headers(sequence = {
    "UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY", "ADDRESS_FROM", "ADDRESS_TO"
}, extract = true)
@FieldDefaults(level = PRIVATE)
public class EveryTradeBeanV3_1 extends ExchangeBean {

    @Parsed(field = "UID")
    String uid;
    Instant date;
    Currency symbolBase;
    Currency symbolQuote;
    TransactionType action;
    @Parsed(field = "QUANTY", defaultNullRead = "0")
    BigDecimal quantity;
    @Parsed(field = "PRICE", defaultNullRead = "0")
    BigDecimal price;
    @Parsed(field = "FEE", defaultNullRead = "0")
    BigDecimal fee;
    Currency feeCurrency;
    @Parsed(field = "REBATE", defaultNullRead = "0")
    BigDecimal rebate;
    Currency rebateCurrency;
    @Parsed(field = "ADDRESS_FROM")
    String addressFrom;
    @Parsed(field = "ADDRESS_TO")
    String addressTo;

    @Parsed(field = "DATE")
    @Format(formats = {"dd.MM.yy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String value) {
        String[] symbolParts = value.split("/");
        symbolBase = Currency.fromCode(symbolParts[0]);
        symbolQuote = symbolParts.length > 1 ? Currency.fromCode(symbolParts[1]) : Currency.fromCode(symbolParts[0]);;
    }

    @Parsed(field = "ACTION")
    public void setAction(String value) {
        action = detectTransactionType(value);
    }

    @Parsed(field = "FEE_CURRENCY")
    public void setFeeCurrency(String value) {
        feeCurrency = value == null ? null : Currency.fromCode(value);
    }

    @Parsed(field = "REBATE_CURRENCY")
    public void setRebateCurrency(String value) {
        rebateCurrency = value == null ? null : Currency.fromCode(value);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(symbolBase, symbolQuote);
        validatePositivity(quantity, price, fee, rebate);
        switch (action) {
            case BUY:
            case SELL:
              return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            case FEE:
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE:
                return new TransactionCluster(createRebateTransactionBean(true), List.of());
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        var tx = new DepositWithdrawalImportedTransaction(
            uid,
            date,
            symbolBase,
            symbolQuote,
            action,
            quantity,
            action == DEPOSIT ? addressFrom : addressTo
        );

        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Price can not be zero.");
        }

        var tx = new BuySellImportedTransactionBean(
            uid,               //uuid
            date,               //executed
            symbolBase,         //base
            symbolQuote,        //quote
            action,             //action
            quantity,           //base quantity
            price              //unit price
        );

        return new TransactionCluster(tx, getRelatedTxs());
    }

    private List<ImportedTransactionBean> getRelatedTxs() {
        var related = new ArrayList<ImportedTransactionBean>();
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createFeeTransactionBean(false));
        }

        if (rebate.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createRebateTransactionBean(false));
        }
        return related;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        final boolean feeCurrencyIsBase = Objects.equals(feeCurrency, symbolBase);
        final boolean feeCurrencyIsQuote = Objects.equals(feeCurrency, symbolQuote);
        if (!feeCurrencyIsBase && !feeCurrencyIsQuote) {
            throw new DataValidationException(String.format(
                "Fee currency '%s' differs to base '%s' and to quote '%s'.",
                feeCurrency,
                symbolBase,
                symbolQuote
            ));
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            symbolBase,
            symbolQuote,
            TransactionType.FEE,
            fee,
            feeCurrency
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        final boolean rebateCurrencyIsBase = Objects.equals(rebateCurrency, symbolBase);
        final boolean rebateCurrencyIsQuote = Objects.equals(rebateCurrency, symbolQuote);
        if (!rebateCurrencyIsBase && !rebateCurrencyIsQuote) {
            throw new DataValidationException(String.format(
                "Rebate currency '%s' differs to base '%s' and to quote '%s'.",
                rebateCurrency,
                symbolBase,
                symbolQuote
            ));
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            symbolBase,
            symbolQuote,
            TransactionType.REBATE,
            rebate,
            rebateCurrency
        );
    }
}
