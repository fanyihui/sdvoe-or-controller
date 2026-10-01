package com.or.sdvoe.schedule;

import com.or.sdvoe.domain.CaseStatus;
import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SurgeryCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/** 本手术室排班列表服务。 */
public class ScheduleService {

    private final OperatingRoom operatingRoom;
    private final ScheduleRepository repository;

    public ScheduleService(OperatingRoom operatingRoom, ScheduleRepository repository) {
        this.operatingRoom = Objects.requireNonNull(operatingRoom);
        this.repository = Objects.requireNonNull(repository);
    }

    public Map<String, Object> listTodaySchedule() {
        List<SurgeryCase> cases = repository.getCases();
        long inProgress = cases.stream().filter(c -> c.getStatus() == CaseStatus.IN_PROGRESS).count();
        long scheduled = cases.stream().filter(c -> c.getStatus() == CaseStatus.SCHEDULED
                || c.getStatus() == CaseStatus.PREP).count();
        long done = cases.stream().filter(c -> c.getStatus() == CaseStatus.DONE).count();

        List<Map<String, Object>> caseMaps = new ArrayList<>();
        for (SurgeryCase c : cases) {
            caseMaps.add(c.toSummaryMap());
        }

        Map<String, Object> or = new LinkedHashMap<>();
        or.put("id", operatingRoom.getId());
        or.put("name", operatingRoom.getName());
        or.put("building", operatingRoom.getBuilding());
        or.put("floor", operatingRoom.getFloor());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("inProgress", inProgress);
        summary.put("scheduled", scheduled);
        summary.put("done", done);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operatingRoom", or);
        result.put("date", repository.getDate());
        result.put("summary", summary);
        result.put("cases", caseMaps);
        return result;
    }

    public SurgeryCase getCase(String caseId) {
        return repository.findCase(caseId)
                .orElseThrow(() -> new NoSuchElementException("case not found: " + caseId));
    }

    public ScheduleRepository repository() {
        return repository;
    }
}
