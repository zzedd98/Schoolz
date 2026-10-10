package ma.schoolz.erp.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsAStrongPassword() {
        assertThatCode(() -> policy.validate("UnePhrase!2026", "personne@example.ma"))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsAllRelevantViolations() {
        assertThatThrownBy(() -> policy.validate("admin", "admin@example.ma"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(
                                                ((PasswordPolicyException) exception).violations())
                                        .contains(
                                                "password.length",
                                                "password.uppercase",
                                                "password.digit",
                                                "password.symbol",
                                                "password.personal"));
    }
}
