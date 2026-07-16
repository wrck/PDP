package com.pdp.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundaryTest {

    @Test
    void platformModulesMustKeepDeclaredBoundariesAndRemainAcyclic() {
        ApplicationModules.of("com.pdp").verify();
    }
}
