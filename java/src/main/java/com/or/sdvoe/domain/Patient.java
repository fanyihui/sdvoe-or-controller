package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Patient {
    private final String id;
    private final String name;
    private final String gender;
    private final int age;
    private final String bed;
    private final String bloodType;
    private final List<String> allergies;
    private final String diagnosis;

    public Patient(
            String id,
            String name,
            String gender,
            int age,
            String bed,
            String bloodType,
            List<String> allergies,
            String diagnosis) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.gender = gender;
        this.age = age;
        this.bed = bed;
        this.bloodType = bloodType;
        this.allergies = allergies == null ? List.of() : List.copyOf(allergies);
        this.diagnosis = diagnosis;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public int getAge() {
        return age;
    }

    public String getBed() {
        return bed;
    }

    public String getBloodType() {
        return bloodType;
    }

    public List<String> getAllergies() {
        return allergies;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("gender", gender);
        m.put("age", age);
        m.put("bed", bed);
        m.put("bloodType", bloodType);
        m.put("allergies", new ArrayList<>(allergies));
        m.put("diagnosis", diagnosis);
        return m;
    }
}
