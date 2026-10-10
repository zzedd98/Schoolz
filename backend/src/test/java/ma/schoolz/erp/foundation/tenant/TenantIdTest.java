package ma.schoolz.erp.foundation.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void parsesAndFormatsStableUuid() {
        UUID value = UUID.fromString("0707f49c-284b-4e38-9e32-6db9745e42a7");

        assertThat(TenantId.from(value.toString())).isEqualTo(new TenantId(value));
        assertThat(new TenantId(value)).hasToString(value.toString());
    }

    @Test
    void rejectsMissingTenant() {
        assertThatNullPointerException().isThrownBy(() -> new TenantId(null));
    }
}
