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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class Scheduling {
    
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
    
    public void everyoneGetsFirstChoice(List<Student> students, Map<String,Class> classes) throws IOException {
        Collections.sort(students);
        int numChoices = students.get(0).choices.size();
        int numPeriods = getNumPeriods(classes);
        
        for (int i = 0; i < numChoices; i++) {
            for(Student s: students) {
                int period = -1;
                while (period == -1 && s.nextChoice < s.choices.size()) {
                    String className = s.choices.get(s.nextChoice++);
                    int availablePeriods = s.isInFirstGrade() ? 3 : numPeriods;
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
            .filter(c->c.isAvailableForPeriod(i))
            .map(c->c.getName())
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
                    .filter(student->(student.isInFirstGrade() && student.assignments.size() < 3) || (!student.isInFirstGrade() && student.assignments.size() < numPeriods))
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
