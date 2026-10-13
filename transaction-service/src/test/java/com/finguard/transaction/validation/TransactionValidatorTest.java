package com.finguard.transaction.validation;

import com.finguard.transaction.config.TransactionProperties;
import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionValidatorTest {

    private final TransactionProperties props = new TransactionProperties(Set.of("USD"), new BigDecimal("1000"), Duration.ofHours(1), 5);
    private final Account usd = Account.builder().accountNumber("FG1").currency("USD").status(Account.Status.ACTIVE).build();

    private CreateTransactionRequest req(String target, String amount, String currency) {
        return new CreateTransactionRequest("FG1", target, new BigDecimal(amount), currency, "US", null, "CARD");
    }

    private TransactionValidator validator() {
        return new TransactionValidator(List.of(new ValidationRules.SupportedCurrency(props),
                new ValidationRules.CurrencyMatchesAccount(), new ValidationRules.AmountWithinLimit(props),
                new ValidationRules.DistinctAccounts()));
    }

    @Test
    void validRequest_passesTheWholeChain() {
        assertThatCode(() -> validator().validate(req("ACC-2", "10", "USD"), usd)).doesNotThrowAnyException();
    }

    @Test
    void eachLinkRejects() {
        assertThatThrownBy(() -> validator().validate(req("ACC-2", "10", "JPY"), usd)).hasMessageContaining("Unsupported currency");
        assertThatThrownBy(() -> validator().validate(req("ACC-2", "1000.01", "USD"), usd)).hasMessageContaining("maximum");
        assertThatThrownBy(() -> validator().validate(req("FG1", "10", "USD"), usd)).hasMessageContaining("must differ");
    }

    @Test
    void firstFailingLinkStopsTheChain() {
        List<String> called = new ArrayList<>();
        var chain = new TransactionValidator(List.of(
                (r, a) -> { called.add("first"); throw new BusinessRuleException("stop"); },
                (r, a) -> called.add("second")));

        assertThatThrownBy(() -> chain.validate(req("ACC-2", "1", "USD"), usd)).isInstanceOf(BusinessRuleException.class);
        assertThat(called).containsExactly("first");
    }
}
