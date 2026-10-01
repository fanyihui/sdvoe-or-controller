package com.or.sdvoe.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SurgeryCase {
    private final String id;
    private final String orId;
    private final String date;
    private final CaseStatus status;
    private final String scheduledStart;
    private final String scheduledEnd;
    private final String actualStart;
    private final String actualEnd;
    private final String procedure;
    private final String procedureCode;
    private final String department;
    private final String surgeon;
    private final String anesthetist;
    private final String scrubNurse;
    private final String circulatingNurse;
    private final String notes;
    private final Patient patient;

    private SurgeryCase(Builder b) {
        this.id = Objects.requireNonNull(b.id);
        this.orId = Objects.requireNonNull(b.orId);
        this.date = b.date;
        this.status = b.status != null ? b.status : CaseStatus.SCHEDULED;
        this.scheduledStart = b.scheduledStart;
        this.scheduledEnd = b.scheduledEnd;
        this.actualStart = b.actualStart;
        this.actualEnd = b.actualEnd;
        this.procedure = b.procedure;
        this.procedureCode = b.procedureCode;
        this.department = b.department;
        this.surgeon = b.surgeon;
        this.anesthetist = b.anesthetist;
        this.scrubNurse = b.scrubNurse;
        this.circulatingNurse = b.circulatingNurse;
        this.notes = b.notes;
        this.patient = Objects.requireNonNull(b.patient);
    }

    public static Builder builder(String id, String orId, Patient patient) {
        return new Builder(id, orId, patient);
    }

    public String getId() {
        return id;
    }

    public String getOrId() {
        return orId;
    }

    public String getDate() {
        return date;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public String getScheduledStart() {
        return scheduledStart;
    }

    public String getScheduledEnd() {
        return scheduledEnd;
    }

    public String getActualStart() {
        return actualStart;
    }

    public String getActualEnd() {
        return actualEnd;
    }

    public String getProcedure() {
        return procedure;
    }

    public String getProcedureCode() {
        return procedureCode;
    }

    public String getDepartment() {
        return department;
    }

    public String getSurgeon() {
        return surgeon;
    }

    public String getAnesthetist() {
        return anesthetist;
    }

    public String getScrubNurse() {
        return scrubNurse;
    }

    public String getCirculatingNurse() {
        return circulatingNurse;
    }

    public String getNotes() {
        return notes;
    }

    public Patient getPatient() {
        return patient;
    }

    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("orId", orId);
        m.put("date", date);
        m.put("status", status.name());
        m.put("statusLabel", status.labelZh());
        m.put("scheduledStart", scheduledStart);
        m.put("scheduledEnd", scheduledEnd);
        m.put("actualStart", actualStart);
        m.put("actualEnd", actualEnd);
        m.put("procedure", procedure);
        m.put("procedureCode", procedureCode);
        m.put("department", department);
        m.put("surgeon", surgeon);
        m.put("anesthetist", anesthetist);
        m.put("patientName", patient.getName());
        m.put("patientGender", patient.getGender());
        m.put("patientAge", patient.getAge());
        m.put("patientId", patient.getId());
        return m;
    }

    public Map<String, Object> toDetailMap() {
        Map<String, Object> m = toSummaryMap();
        m.put("scrubNurse", scrubNurse);
        m.put("circulatingNurse", circulatingNurse);
        m.put("notes", notes);
        m.put("patient", patient.toMap());
        return m;
    }

    public static final class Builder {
        private final String id;
        private final String orId;
        private final Patient patient;
        private String date;
        private CaseStatus status;
        private String scheduledStart;
        private String scheduledEnd;
        private String actualStart;
        private String actualEnd;
        private String procedure;
        private String procedureCode;
        private String department;
        private String surgeon;
        private String anesthetist;
        private String scrubNurse;
        private String circulatingNurse;
        private String notes;

        private Builder(String id, String orId, Patient patient) {
            this.id = id;
            this.orId = orId;
            this.patient = patient;
        }

        public Builder date(String date) {
            this.date = date;
            return this;
        }

        public Builder status(CaseStatus status) {
            this.status = status;
            return this;
        }

        public Builder scheduledStart(String scheduledStart) {
            this.scheduledStart = scheduledStart;
            return this;
        }

        public Builder scheduledEnd(String scheduledEnd) {
            this.scheduledEnd = scheduledEnd;
            return this;
        }

        public Builder actualStart(String actualStart) {
            this.actualStart = actualStart;
            return this;
        }

        public Builder actualEnd(String actualEnd) {
            this.actualEnd = actualEnd;
            return this;
        }

        public Builder procedure(String procedure) {
            this.procedure = procedure;
            return this;
        }

        public Builder procedureCode(String procedureCode) {
            this.procedureCode = procedureCode;
            return this;
        }

        public Builder department(String department) {
            this.department = department;
            return this;
        }

        public Builder surgeon(String surgeon) {
            this.surgeon = surgeon;
            return this;
        }

        public Builder anesthetist(String anesthetist) {
            this.anesthetist = anesthetist;
            return this;
        }

        public Builder scrubNurse(String scrubNurse) {
            this.scrubNurse = scrubNurse;
            return this;
        }

        public Builder circulatingNurse(String circulatingNurse) {
            this.circulatingNurse = circulatingNurse;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public SurgeryCase build() {
            return new SurgeryCase(this);
        }
    }
}
