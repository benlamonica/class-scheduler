package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static us.pojo.scheduling.CSVParser.parseLine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
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
    }
    
    public boolean isInFirstGrade() {
        return grade == 1;
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
            this.grade = IntegerValidator.getInstance().validate(Optional.ofNullable(fields.get("Grade")).orElse("1"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            this.time = LocalDateTime.from(TIME_FORMATTER.parse(fields.get("Date Completed")+" "+fields.get("Time Completed"))).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            this.time = Long.MAX_VALUE;
        }
        this.choices.addAll(choices);
    }
    private Map<String,String> fields = new HashMap<>();
    public List<String> choices = new ArrayList<>();
    public int nextChoice = 0;
    private int grade;
    private long time;
    public SortedMap<Integer, String> assignments = new TreeMap<>();
    
    public void resetAssignment() {
        assignments.clear();
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
                .mapToObj(i->assignments.getOrDefault(i, ""))
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
        Set<String> classes = new HashSet<>(assignments.values());
        int score = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (classes.contains(choices.get(i))) {
                score += choices.size() - i;
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