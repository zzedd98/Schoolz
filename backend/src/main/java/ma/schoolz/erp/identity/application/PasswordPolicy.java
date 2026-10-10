package ma.schoolz.erp.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicy {

    public void validate(String password, String email) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.length() < 12) {
            violations.add("password.length");
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("password.uppercase");
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("password.lowercase");
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            violations.add("password.digit");
        }
        if (password == null || password.chars().allMatch(Character::isLetterOrDigit)) {
            violations.add("password.symbol");
        }
        String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (password != null
                && localPart.length() >= 4
                && password.toLowerCase(Locale.ROOT).contains(localPart)) {
            violations.add("password.personal");
        }
        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(violations);
        }
    }
}
