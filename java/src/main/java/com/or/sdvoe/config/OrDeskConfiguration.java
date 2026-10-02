package com.or.sdvoe.config;

import com.or.sdvoe.adapter.RecorderControlPort;
import com.or.sdvoe.adapter.SoftwareRecorderAdapter;
import com.or.sdvoe.discovery.SdvoeDiscoveryFactory;
import com.or.sdvoe.persistence.MosaicRepository;
import com.or.sdvoe.persistence.RecordingRepository;
import com.or.sdvoe.persistence.RouteRepository;
import com.or.sdvoe.policy.PolicyEngine;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import com.or.sdvoe.workspace.MosaicService;
import com.or.sdvoe.workspace.RecordingService;
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

    @Bean(destroyMethod = "close")
    public MosaicRepository mosaicRepository(DatabaseSettings databaseSettings) {
        return new MosaicRepository(databaseSettings);
    }

    @Bean(destroyMethod = "close")
    public RecordingRepository recordingRepository(DatabaseSettings databaseSettings) {
        return new RecordingRepository(databaseSettings);
    }

    @Bean
    public RecorderControlPort recorderControlPort(OrDeskProperties props) {
        return new SoftwareRecorderAdapter(
                props.getRecordingWorkerId(), Path.of(props.getRecordingOutputDir()));
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
    public MosaicService mosaicService(
            OrControllerConfig config,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            FabricOrchestrator fabricOrchestrator,
            WorkspaceRoutingService workspaceRoutingService,
            MosaicRepository mosaicRepository) {
        return new MosaicService(
                config.getOperatingRoom().getId(),
                scheduleService,
                deviceInventoryService,
                fabricOrchestrator,
                workspaceRoutingService,
                mosaicRepository);
    }

    @Bean
    public RecordingService recordingService(
            OrControllerConfig config,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            RecorderControlPort recorderControlPort,
            RecordingRepository recordingRepository) {
        return new RecordingService(
                config.getOperatingRoom().getId(),
                scheduleService,
                deviceInventoryService,
                recorderControlPort,
                recordingRepository);
    }

    @Bean
    public SurgeryWorkspaceService surgeryWorkspaceService(
            OrControllerConfig config,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            WorkspaceRoutingService workspaceRoutingService,
            MosaicService mosaicService,
            RecordingService recordingService) {
        return new SurgeryWorkspaceService(
                config.getOperatingRoom(),
                scheduleService,
                deviceInventoryService,
                workspaceRoutingService,
                mosaicService,
                recordingService);
    }

    @Bean
    public ApplicationRunner restoreRoutesOnStartup(
            DatabaseSettings databaseSettings,
            WorkspaceRoutingService workspaceRoutingService,
            MosaicService mosaicService,
            RecordingService recordingService,
            RouteRepository routeRepository,
            OrControllerConfig config) {
        return args -> {
            if (!databaseSettings.isRestoreRoutesOnStartup()) {
                log.info("Route restore on startup disabled");
                return;
            }
            int[] stats = workspaceRoutingService.restorePersistedRoutes();
            int[] mosaicStats = mosaicService.restorePersistedMosaics();
            int[] recordingStats = recordingService.restorePersistedRecordings();
            log.info(
                    "OR Desk Spring Boot ready | OR={} store={} remote={} backupDir={} restoredOk={} restoredFailed={} persisted={} mosaicOk={} mosaicFailed={} recordingOk={} recordingFailed={}",
                    config.getOperatingRoom().getId(),
                    routeRepository.storageLabel(),
                    databaseSettings.isRemoteServer(),
                    routeRepository.getBackupDir(),
                    stats[0],
                    stats[1],
                    routeRepository.countByOr(config.getOperatingRoom().getId()),
                    mosaicStats[0],
                    mosaicStats[1],
                    recordingStats[0],
                    recordingStats[1]);
        };
    }
}
