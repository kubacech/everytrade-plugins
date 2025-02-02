package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BittrexBeanV3Test {
    private static final String HEADER_CORRECT = "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity," +
        "QuantityRemaining,Commission,Price,PricePerUnit,IsConditional,Condition,ConditionTarget," +
        "ImmediateOrCancel,Closed,TimeInForceTypeId,TimeInForce\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testWrongHeader() {
        String headerWrong = "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity," +
            "QuantityRemaining,Commission,Price,PricePerUnit,IsConditional,Condition,ConditionTarget," +
            "ImmediateOrCancel,CloseX,TimeInForceTypeId,TimeInForce\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingSellTransaction() {
        final String row = "d-01,USD-LTC,7/31/2020 11:32:59 AM,MARKET_SELL,,0.50000000,0.00000000,0.05708940," +
            "28.54470380,57.08940760,False,,0.00000000,True,7/31/2020 11:32:59 AM,2,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "d-01",
                Instant.parse("2020-07-31T11:32:59Z"),
                Currency.LTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.5"),
                new BigDecimal("57.0894076")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "d-01" + FEE_UID_PART,
                    Instant.parse("2020-07-31T11:32:59Z"),
                    Currency.LTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.0570894"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingBuyTransaction() {
        final String row = "d-01,USD-LTC,7/31/2020 11:35:21 AM,CEILING_MARKET_BUY,,0.49750262,0.00000000,0.05686182," +
            "28.43078222,57.14699999,False,,0.00000000,True,7/31/2020 11:35:21 AM,2,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "d-01",
                Instant.parse("2020-07-31T11:35:21Z"),
                Currency.LTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.49750262"),
                new BigDecimal("57.14699999")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "d-01" + FEE_UID_PART,
                    Instant.parse("2020-07-31T11:35:21Z"),
                    Currency.LTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.05686182"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownExchange() {
        final String row = "d-01,USD-XXX,7/31/2020 11:32:59 AM,MARKET_SELL,,0.50000000,0.00000000,0.05708940," +
            "28.54470380,57.08940760,False,,0.00000000,True,7/31/2020 11:32:59 AM,2,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("USD-XXX"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "d-01,USD-LTC,7/31/2020 11:32:59 AM,BUY,,0.50000000,0.00000000,0.05708940," +
            "28.54470380,57.08940760,False,,0.00000000,True,7/31/2020 11:32:59 AM,2,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BUY")));
    }
}