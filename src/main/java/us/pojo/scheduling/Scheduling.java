package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.IntegerValidator;

public class Scheduling {
    
    private static final Pattern CLEANUP_CLASS = Pattern.compile("^\\s*([^(]+?)\\s*\\(.+\\)\\s*$");
    private static final Pattern NEXT_CELL = Pattern.compile("(\"([^\"]*)\"|([^\",])*),");
    
    private static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        Matcher m = NEXT_CELL.matcher(line);
        String end = null;
        while(m.find()) {
            if (m.group(2) != null) {
                values.add(m.group(2));
            } else {
                values.add(m.group(1));
            }
            end = line.substring(m.end());
        }

        if (StringUtils.isNotBlank(end)) {
            values.add(end);
        }

        return values;
    }
    
    private static DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/YY hh:mm a");
    private static class Student {
        public Student(Student copy) {
            this.grade = copy.grade;
            this.choices = new ArrayList<>(copy.choices);
            this.assignments = new TreeMap<>(copy.assignments);
            this.fields = copy.fields;
            this.time = copy.time;
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
            return fields.get("STUDENT First Name") + " " + fields.get("STUDENT Last Name");
        }
        
        public int getGrade() {
            return grade;
        }
        
        public String toCsv(int numPeriods, int maxScore, int firstGradeMaxScore) {
            StringBuilder buf = new StringBuilder(getName()+","+getGrade()+","+getHappinessScore(grade!=1?maxScore:firstGradeMaxScore)+","+assignments.size()+",");
            buf.append(IntStream.range(0, numPeriods).mapToObj(i->assignments.getOrDefault(i, "")).collect(joining("\",\"","\"","\"")));
            return buf.toString();
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
    }
    
    private static class Class {
        private static class Period {
            public int maxStudents;
            public List<Student> students = new ArrayList<>();
            public Period(Period copy) {
                this(copy.maxStudents);
                students = new ArrayList<>(copy.students);
            }
            public Period(Integer maxStudents) {
                this.maxStudents = maxStudents == null ? 0 : maxStudents;
            }
            public boolean addStudent(Student s) {
                if (students.size() < maxStudents) {
                    students.add(s);
                    return true;
                }
                return false;
            }
        }

        public List<Period> periods;
        public String name;
        public int minGrade;
        
        public Class(Class copy) {
            this.name = copy.name;
            this.periods = copy.periods.stream().map(Period::new).collect(toList());
            this.minGrade = copy.minGrade;
        }
        
        public void resetAssignment() {
            periods.stream().forEach(p->p.students.clear());
        }
        
        public Class(String line) {
            List<String> fields = parseLine(line);
            IntegerValidator intValidator = IntegerValidator.getInstance();
            name = fields.get(1);
            periods = fields.subList(5, fields.size()).stream().map(intValidator::validate).map(Period::new).collect(toList());
            minGrade = Optional.ofNullable(fields.get(2)).map(intValidator::validate).orElse(1);
        }

        public int addStudent(Student s, Set<Integer> availablePeriods) {
            for (int period : availablePeriods) {
                if (periods.get(period).addStudent(s)) {
                    return period;
                }
            }
            return -1;
        }
        
        public String getCsvHeader() {
            return "Name,"+IntStream.range(1, periods.size()+1).mapToObj(p->"Session " + p).collect(joining(","))+",MinGrade";
        }
        
        public String toCsv() {
            return "\""+name+"\","+periods.stream().map(p->String.valueOf(p.students.size())).collect(joining(","))+","+minGrade;
        }
    }
    
    private Map<String, Class> classes;
    private List<Student> students;

    private BufferedReader getReader(String file) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("utf8")));
    }
    
    public Scheduling(String classFilename, String studentFilename) throws IOException {
        try(BufferedReader classFile = getReader(classFilename);
            BufferedReader studentFile = getReader(studentFilename)) {

            List<String> header = Arrays.asList(studentFile.readLine().split(","));
            List<Student> students = studentFile.lines().map(l->new Student(header, l)).filter(s->s.choices.size() > 0).collect(toList());
            classFile.readLine(); // strip off header
            List<Class> classes = classFile.lines().map(Class::new).collect(toList());
            
            this.classes = classes.stream().collect(toMap(c->c.name, c->c));
            this.students = students;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static final Comparator<Student> STACK_RANK_BY_GRADE = new Comparator<Student>() {
        @Override
        public int compare(Student o1, Student o2) {
            int compare = Integer.compare(o2.getGrade(), o1.getGrade());
            if (compare == 0) {
                return Long.compare(o2.time, o1.time);
            }
            return compare;
        }
    };
    
    public void everyoneGetsFirstChoice(List<Student> students, Map<String,Class> classes) throws IOException {
        Collections.sort(students, STACK_RANK_BY_GRADE);
        int numChoices = students.get(0).choices.size();
        int numPeriods = getNumPeriods(classes);
        
        for (int i = 0; i < numChoices; i++) {
            for(Student s: students) {
                int period = -1;
                while (period == -1 && s.nextChoice < s.choices.size()) {
                    String className = s.choices.get(s.nextChoice++);
                    int availablePeriods = s.grade > 1 ? numPeriods : 3;
                    Set<Integer> available = IntStream.range(0, availablePeriods).filter(p->!s.assignments.containsKey(p)).mapToObj(p->p).collect(toSet());
                    Class c = classes.get(className);
                    if (c == null || StringUtils.isBlank(className)) {
                        System.err.println("Unknown Class: " + className);
                    } else {
                        period = c.addStudent(s, available);
                        if (period != -1) {
                            s.assignments.put(period, className);
                        }
                    }
                }
            }
        }
    }
    
    private int getNumPeriods(Map<String, Class> classes) {
        return classes.values().stream().findFirst().map(c->c.periods.size()).orElse(0);
    }
    
    private void outputResults(String prefix, List<Student> students, Map<String, Class> classes) throws IOException {
        int numPeriods = getNumPeriods(classes);
        int maxScore = 0;
        int firstGradeMaxScore = 0;
        for (int i = 0; i < numPeriods; i++) {
            maxScore += (10-i);
            if (i < 3) {
                firstGradeMaxScore += (10-i);
            }
        }
        
        String classHeader = IntStream.range(1, numPeriods+1).mapToObj(i->"Session "+i).collect(joining(","));
        try (PrintWriter assignments = new PrintWriter(new FileWriter("/Users/blamoni/Desktop/"+prefix+"assignments.csv"))) {
            assignments.println("Name,Grade,Happiness,NumClasses,"+classHeader);
            for(Student s: students) {
                assignments.println(s.toCsv(numPeriods, maxScore, firstGradeMaxScore));
            }
        }
        
        
        try (PrintWriter classSizes = new PrintWriter(new FileWriter("/Users/blamoni/Desktop/"+prefix+"class_sizes.csv"))) {
            classSizes.println(classes.values().iterator().next().getCsvHeader());
            for(Class c: classes.values()) {
                classSizes.println(c.toCsv());
            }
        }
    }
    private List<Student> copyStudents(List<Student> s) {
        return s.stream().map(Student::new).collect(toList());
    }
    
    private Map<String, Class> copyClasses(Map<String, Class> c) {
        return c.entrySet().stream()
            .map(e->Pair.of(e.getKey(), new Class(e.getValue())))
            .collect(toMap(k->k.getLeft(), v->v.getRight()));
    }
    
    private Set<String> getClassesForPeriod(int i) {
        return classes.values().stream()
            .filter(c->c.periods.get(i).maxStudents > 0)
            .map(c->c.name)
            .collect(toSet());
    }
    
    private void rearrangeChoicesForMissingPeriods(List<Student> students) {
        int numPeriods = getNumPeriods(classes);
        students.forEach(student->{
            for (int i = 0; i < numPeriods; i++) {
                if (!student.assignments.containsKey(i)) {
                    Set<String> choices = new LinkedHashSet<>(student.choices);
                    Set<String> classes = getClassesForPeriod(i);
                    choices.retainAll(classes);
                    choices.removeAll(student.assignments.values());
                    if (!choices.isEmpty()) {
                        student.choices.add(0, choices.iterator().next());
                        student.choices = new ArrayList<>(new LinkedHashSet<>(student.choices));
                    } else {
                        //System.out.println(student.getName() + " doesn't have any choices for session " + (i+1));
                    }
                }
            }
        });
    }
    
    public void run(String prefix) throws IOException {
        List<Student> s = copyStudents(this.students);
        Map<String, Class> c = copyClasses(this.classes);
        int numPeriods = getNumPeriods(classes);
        
//        s.stream()
//            .filter(student->student.choices.size() < 10)
//            .forEach(student->System.out.println(student.getName() + " only has " + student.choices.size() + " unique choices."));
        

        Pair<List<Student>, Map<String,Class>> bestRun = Pair.of(null, null);
        int bestMissing = Integer.MAX_VALUE;
        int numStudentsMissingClasses = -1;
        int tries = 100;
        
        
        while (tries-- > 0) {
            everyoneGetsFirstChoice(s, c);
            
            List<Student> studentsWithoutAllClasses = s.stream()
                    .filter(student->(student.grade == 1 && student.assignments.size() < 3) || (student.grade != 1 && student.assignments.size() < numPeriods))
                    .collect(toList());

            numStudentsMissingClasses = studentsWithoutAllClasses.size();
            
            if (numStudentsMissingClasses < bestMissing) {
                bestMissing = numStudentsMissingClasses;
                bestRun = Pair.of(copyStudents(s), copyClasses(c));
            }
            
            // do something to make it so that more students get their classes filled.
            rearrangeChoicesForMissingPeriods(studentsWithoutAllClasses);
            
            c = copyClasses(this.classes);
            c.values().stream().forEach(Class::resetAssignment);
            s.stream().forEach(Student::resetAssignment);
        }
        
        System.out.println(bestMissing + " students don't have full schedules.");
        outputResults(prefix, bestRun.getLeft(), bestRun.getRight());
    }
    
    public static void main(String[] args) throws Exception {
        
        long start = System.currentTimeMillis();
        Scheduling scheduling = new Scheduling("/Users/blamoni/Desktop/EMD 2018 - Class Counts & Grades.csv", "/Users/blamoni/Desktop/EMD 2018 - FINAL Entry for  Submission.csv");
        scheduling.run("normal-");
        Scheduling rainScheduling = new Scheduling("/Users/blamoni/Desktop/EMD 2018 - Class Counts & Grades - Rain Plan.csv", "/Users/blamoni/Desktop/EMD 2018 - FINAL Entry for  Submission.csv");
        rainScheduling.run("rain-");
        System.out.println("Took " + (System.currentTimeMillis() - start) + " ms.");
        
    }
}
