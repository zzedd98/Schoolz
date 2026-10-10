package ma.schoolz.erp.foundation.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnsafeFileException extends RuntimeException {
    public UnsafeFileException(String message) {
        super(message);
    }
}
