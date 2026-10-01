package com.or.sdvoe.config;

import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.persistence.RouteRepository;
import com.or.sdvoe.policy.PolicyEngine;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import com.or.sdvoe.workspace.SurgeryWorkspaceService;
import com.or.sdvoe.workspace.WorkspaceRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(OrDeskProperties.class)
public class OrDeskConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OrDeskConfiguration.class);

    @Bean
    public OrControllerConfig orControllerConfig(OrDeskProperties props) {
        Path local = Path.of("config/or-controller.yaml");
        if (Files.isRegularFile(local)) {
            return OrControllerConfig.load(local);
        }
        Path sibling = Path.of("../config/or-controller.yaml");
        if (Files.isRegularFile(sibling)) {
            return OrControllerConfig.load(sibling);
        }
        return OrControllerConfig.loadClasspath(props.getConfigResource());
    }

    @Bean
    public DatabaseSettings databaseSettings(OrControllerConfig config) {
        return DatabaseSettingsFactory.resolve(config.getDatabaseSettings());
    }

    @Bean(destroyMethod = "close")
    public RouteRepository routeRepository(DatabaseSettings databaseSettings) {
        return new RouteRepository(databaseSettings);
    }

    @Bean
    public ScheduleRepository scheduleRepository(OrDeskProperties props) {
        Path local = Path.of("config/schedule.yaml");
        if (Files.isRegularFile(local)) {
            return ScheduleRepository.load(local);
        }
        Path sibling = Path.of("../config/schedule.yaml");
        if (Files.isRegularFile(sibling)) {
            return ScheduleRepository.load(sibling);
        }
        return ScheduleRepository.loadClasspath(props.getScheduleResource());
    }

    @Bean
    public ScheduleService scheduleService(OrControllerConfig config, ScheduleRepository scheduleRepository) {
        return new ScheduleService(config.getOperatingRoom(), scheduleRepository);
    }

    @Bean
    public PolicyEngine policyEngine(OrDeskProperties props) {
        Path local = Path.of("config/routing-policies.yaml");
        if (Files.isRegularFile(local)) {
            return new PolicyEngine(local);
        }
        Path sibling = Path.of("../config/routing-policies.yaml");
        if (Files.isRegularFile(sibling)) {
            return new PolicyEngine(sibling);
        }
        try {
            InputStream in = new ClassPathResource(props.getPolicyResource()).getInputStream();
            return new PolicyEngine(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load policy: " + props.getPolicyResource(), e);
        }
    }

    @Bean
    public SdvoeDeviceInventoryService sdvoeDeviceInventoryService(OrControllerConfig config) {
        return new SdvoeDeviceInventoryService(
                config.getOperatingRoom(),
                SdvoeDiscoveryFactory.create(config));
    }

    @Bean
    public FabricOrchestrator fabricOrchestrator() {
        return WorkspaceRoutingService.createDefaultOrchestrator();
    }

    @Bean
    public WorkspaceRoutingService workspaceRoutingService(
            OrControllerConfig config,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            PolicyEngine policyEngine,
            FabricOrchestrator fabricOrchestrator,
            RouteRepository routeRepository) {
        return new WorkspaceRoutingService(
                config.getOperatingRoom().getId(),
                scheduleService,
                deviceInventoryService,
                policyEngine,
                fabricOrchestrator,
                routeRepository);
    }

    @Bean
    public SurgeryWorkspaceService surgeryWorkspaceService(
            OrControllerConfig config,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            WorkspaceRoutingService workspaceRoutingService) {
        return new SurgeryWorkspaceService(
                config.getOperatingRoom(),
                scheduleService,
                deviceInventoryService,
                workspaceRoutingService);
    }

    @Bean
    public ApplicationRunner restoreRoutesOnStartup(
            DatabaseSettings databaseSettings,
            WorkspaceRoutingService workspaceRoutingService,
            RouteRepository routeRepository,
            OrControllerConfig config) {
        return args -> {
            if (!databaseSettings.isRestoreRoutesOnStartup()) {
                log.info("Route restore on startup disabled");
                return;
            }
            int[] stats = workspaceRoutingService.restorePersistedRoutes();
            log.info(
                    "OR Desk Spring Boot ready | OR={} store={} remote={} backupDir={} restoredOk={} restoredFailed={} persisted={}",
                    config.getOperatingRoom().getId(),
                    routeRepository.storageLabel(),
                    databaseSettings.isRemoteServer(),
                    routeRepository.getBackupDir(),
                    stats[0],
                    stats[1],
                    routeRepository.countByOr(config.getOperatingRoom().getId()));
        };
    }
}
