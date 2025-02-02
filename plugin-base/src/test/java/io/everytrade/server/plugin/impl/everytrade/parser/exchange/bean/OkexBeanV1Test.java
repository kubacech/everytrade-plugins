package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OkexBeanV1Test {
    private static final String HEADER_CORRECT
        = "\uFEFFOrder ID,\uFEFFTrade ID,\uFEFFTrade Time,\uFEFFPairs,\uFEFFAmount,\uFEFFPrice,\uFEFFTotal," +
        "\uFEFFtaker/maker,\uFEFFFee,\uFEFFunit\r\n";

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
        final String headerWrong = "\uFEFFOrder ID,\uFEFFTrade ID,\uFEFFTrade Time,\uFEFFXPairs,\uFEFFAmount," +
            "\uFEFFPrice,\uFEFFTotal,\uFEFFtaker/maker,\uFEFFFee,\uFEFFunit\r\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "\uFEFF2,0,2020-09-25 17:27:02,ltc_usdt,2.273693,46.17," +
            "-104.976405810000002816 USDT,taker,-0.0034105395 LTC,LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "0",
                Instant.parse("2020-09-25T17:27:02Z"),
                Currency.LTC,
                Currency.USDT,
                TransactionType.BUY,
                new BigDecimal("2.273693"),
                new BigDecimal("46.17")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + FEE_UID_PART,
                    Instant.parse("2020-09-25T17:27:02Z"),
                    Currency.LTC,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("0.1574646087"),
                    Currency.LTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawSellTransaction() {
        final String row = "\uFEFF0,1,2020-09-25 16:58:58,ltc_usdt,-2.298821,45.87," +
            "105.446919269999995095 USDT,taker,-0.158170378905 USDT,LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2020-09-25T16:58:58Z"),
                Currency.LTC,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("2.298821"),
                new BigDecimal("45.87")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1" + FEE_UID_PART,
                    Instant.parse("2020-09-25T16:58:58Z"),
                    Currency.LTC,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("0.158170378905"),
                    Currency.USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testUnknonwExchangePair() {
        final String row =  "\uFEFF2,0,2020-09-25 17:27:02,ltc_us,2.273693,46.17," +
            "-104.976405810000002816 USDT,taker,-0.0034105395 LTC,LTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Unable to set value 'ltc_us'"));
    }

    @Test
    void testDifferUnitCurrency() {
        final String row =  "\uFEFF2,0,2020-09-25 17:27:02,ltc_usdt,2.273693,46.17," +
            "-104.976405810000002816 USDT,taker,-0.0034105395 LTC,ETH\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Pairs-base currency 'LTC' differs from unit currency 'ETH'"));
    }

    @Test
    void testDifferTotalCurrency() {
        final String row =  "\uFEFF2,0,2020-09-25 17:27:02,ltc_eth,2.273693,46.17," +
            "-104.976405810000002816 USDT,taker,-0.0034105395 LTC,LTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Pairs-quote currency 'ETH' differs from Total currency 'USDT'"));
    }
}