package cn.itcast.demo.mymmorpg.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.testng.annotations.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块依赖方向守卫：common 共享包不应依赖 Spring Boot Application 入口。
 */
public class ModuleDependencyArchTest {

    @Test
    public void shared_packages_should_not_depend_on_spring_boot_application() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "cn.itcast.demo.mymmorpg.shop",
                        "cn.itcast.demo.mymmorpg.aoi",
                        "cn.itcast.demo.mymmorpg.world",
                        "cn.itcast.demo.mymmorpg.anticheat",
                        "cn.itcast.demo.mymmorpg.observability",
                        "cn.itcast.demo.mymmorpg.mq");
        if (classes.isEmpty()) {
            return;
        }
        noClasses()
                .that().resideInAnyPackage(
                        "cn.itcast.demo.mymmorpg.shop..",
                        "cn.itcast.demo.mymmorpg.aoi..",
                        "cn.itcast.demo.mymmorpg.world..",
                        "cn.itcast.demo.mymmorpg.anticheat..",
                        "cn.itcast.demo.mymmorpg.observability..",
                        "cn.itcast.demo.mymmorpg.mq..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Application")
                .because("共享底座不得依赖各服务 Application 启动类")
                .check(classes);
    }
}
