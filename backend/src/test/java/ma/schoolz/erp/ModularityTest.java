package ma.schoolz.erp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesHaveNoCyclesOrIllegalDependencies() {
        ApplicationModules.of(SchoolErpApplication.class).verify();
    }
}
