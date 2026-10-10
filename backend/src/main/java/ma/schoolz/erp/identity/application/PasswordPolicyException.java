package ma.schoolz.erp.identity.application;

import java.util.List;

public final class PasswordPolicyException extends RuntimeException {

    private final transient List<String> violations;

    public PasswordPolicyException(List<String> violations) {
        super("Password does not satisfy policy");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
