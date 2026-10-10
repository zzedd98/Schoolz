package ma.schoolz.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

@Modulith(systemName = "SchoolZ ERP", sharedModules = "foundation")
public class SchoolErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolErpApplication.class, args);
    }
}
