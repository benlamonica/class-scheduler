package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static us.pojo.scheduling.CSVParser.parseLine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.IntegerValidator;

public class Student implements Comparable<Student> {
    private static DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/YY hh:mm a");
    private static final Pattern CLEANUP_CLASS = Pattern.compile("^\\s*([^(]+?)\\s*\\(.+\\)\\s*$");

    public Student(Student copy) {
        this.grade = copy.grade;
        this.choices = new ArrayList<>(copy.choices);
        this.assignments = new TreeMap<>(copy.assignments);
        this.fields = copy.fields;
        this.time = copy.time;
        this.originalChoices = copy.originalChoices;
    }
    
    public boolean isInFirstGrade() {
        return grade == 1 || Optional.ofNullable(fields.get("Teacher")).map(t->t.startsWith("1")).orElse(false);
    }
    
    public Student(List<String> header, String line) {
        List<String> values = parseLine(line);
        Set<String> choices = new LinkedHashSet<>();
        
        for (int i = 0; i < header.size() && i < values.size(); i++) {
            fields.put(header.get(i), values.get(i));
            if (header.get(i).endsWith("choice")) {
                String c = values.get(i);
                if (c != null) {
                    Matcher m = CLEANUP_CLASS.matcher(c);
                    if (m.find()) {
                        c = m.group(1);
                    }
                }
                if (StringUtils.isNotBlank(c)) {
                    choices.add(c);
                }
            }
        }
        try {
            this.grade = IntegerValidator.getInstance().validate(Optional.ofNullable(fields.get("Grade")).orElse("2"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            this.time = LocalDateTime.from(TIME_FORMATTER.parse(fields.get("Date Completed")+" "+fields.get("Time Completed"))).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            this.time = Long.MAX_VALUE;
        }
        this.choices.addAll(choices);
        this.originalChoices = new ArrayList<>(this.choices);
    }
    private Map<String,String> fields = new HashMap<>();
    public List<String> choices = new ArrayList<>();
    public List<String> originalChoices = new ArrayList<>();
    public int nextChoice = 0;
    private int grade;
    private long time;
    public TreeMap<Integer, Assignment> assignments = new TreeMap<>();

    public static class Assignment {
        public Assignment(String name, boolean locked) {
            this.name = name;
            this.locked = locked;
        }
        String name;
        boolean locked;
    }
    
    public void resetAssignment() {
        List<Integer> toRemove = assignments.entrySet().stream().filter(e->!e.getValue().locked).map(e->e.getKey()).collect(toList());
        toRemove.forEach(assignments::remove);
        nextChoice = 0;
    }
    
    public void removeChoicesThatAreAlreadyAssigned() {
        Set<String> newChoices = new LinkedHashSet<>(choices);
        assignments.values().stream().map(a->a.name).forEach(newChoices::remove);
        this.choices = new ArrayList<>(newChoices);
        nextChoice = 0;
    }
    
    public String getName() {
        return getFirstName() + " " + getLastName();
    }
    
    public int getGrade() {
        return grade;
    }
    
    public String toCsv(int numPeriods, int maxScore, int firstGradeMaxScore, Map<String, Class> classes) {
        StringBuilder buf = new StringBuilder(getLastName()+","+getFirstName()+","+getGrade()+","+getTeacher()+","+getHappinessScore(grade!=1?maxScore:firstGradeMaxScore)+","+assignments.size()+",");
        buf.append(IntStream.range(0, numPeriods)
                .mapToObj(i->assignments.getOrDefault(i, new Assignment("", false)).name)
                .flatMap(c->Stream.of(c, Optional.ofNullable(classes.get(c)).map(Class::getLocation).orElse("")))
                .collect(joining("\",\"","\"","\"")));
        return buf.toString();
    }
    
    private String getTeacher() {
        return fields.get("Teacher");
    }

    private String getFirstName() {
        return fields.get("STUDENT First Name");
    }

    private String getLastName() {
        return fields.get("STUDENT Last Name");
    }

    public int getHappinessScore(int maxScore) {
        Set<String> classes = assignments.values().stream().map(a->a.name).collect(toSet());
        int score = 0;
        for (int i = 0; i < originalChoices.size(); i++) {
            if (classes.contains(originalChoices.get(i))) {
                score += originalChoices.size() - i;
            }
        }
        
        return (int) ((score / (maxScore * 1.0)) * 100);
    }

    public int compareTo(Student o2) {
        int compare = Integer.compare(o2.getGrade(), getGrade());
        if (compare == 0) {
            return Long.compare(o2.time, time);
        }
        return compare;
    }
}