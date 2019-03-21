package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.amazonaws.util.IOUtils;

import us.pojo.scheduling.Class.Period;
import us.pojo.scheduling.Student.Assignment;

public class Scheduling {
    private Map<String, Class> classes;
    private Map<String, Class> rainClasses;
    private List<Student> students;
    private List<Student> rainStudents;
    private int numChoices = 10;
    private ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    private PrintWriter err = new PrintWriter(errStream);
    private boolean randomlyFillMissingClasses = false;

    private BufferedReader getReader(InputStream file) throws IOException {
        if (file == null) {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(new byte[0])));
        } else {
            return new BufferedReader(new InputStreamReader(file, Charset.forName("utf8")));
        }
    }
    
    public Scheduling(InputStream classesStream, InputStream rainClassesStream, InputStream studentStream, InputStream existingScheduleStream, InputStream existingRainScheduleStream, boolean randomlyFillMissingClasses) {
    	parse(classesStream, rainClassesStream, studentStream, existingScheduleStream, existingRainScheduleStream);
    	numChoices = getMaxChoices(students);
    	this.randomlyFillMissingClasses = randomlyFillMissingClasses;
    }

    private Map<String, Class> parseClassFile(BufferedReader classFile) throws IOException {
    	if (!classFile.ready()) {
    		throw new RuntimeException("Unable to parse classes.csv, file not provided.");
    	}
    	
    	Map<String, Integer> classHeader = new HashMap<>();
        
        int classCol = 0;
        for (String key : classFile.readLine().split(",")) {
        	classHeader.put(key.toLowerCase(), classCol++);
        }
        
        List<Class> classes = classFile.lines().map(l->new Class(l, classHeader)).collect(toList());
        return classes.stream().filter(c->StringUtils.isNotBlank(c.name)).collect(toMap(c->c.name, c->c));
    }
    
    private boolean parseExistingStudents(Map<String, Class> classes, Map<String, Student> students, BufferedReader existingFile) throws IOException {
    	if (existingFile.ready()) {
	        List<String> existingHeader = CSVParser.parseLine(existingFile.readLine());
	        AtomicInteger existingStudentLine = new AtomicInteger(1);
	        existingFile.lines().forEach(line->{
	            List<String> fields = CSVParser.parseLine(line);
	            Map<String, String> mapping = new HashMap<>();
	            for (int i = 0; i < existingHeader.size() && i < fields.size(); i++) {
	                mapping.put(existingHeader.get(i), fields.get(i));
	            }
	            Student s = students.get(mapping.get("name"));
	            if (s == null) {
	                err.println("Unable to find student " + mapping.get("name"));
	                s = new Student(existingHeader, line, existingStudentLine.getAndIncrement());
	                students.put(s.getName(), s);
	            }
	        });
	        return true;
    	}
    	
    	return false;
    }
    
    private Map<String, Student> parseStudents(BufferedReader studentFile) throws IOException {
        List<String> header = Arrays.asList(studentFile.readLine().split(","));
        AtomicInteger studentLine = new AtomicInteger(1);
        Map<String, Student> students = studentFile.lines()
        		.map(l->new Student(header, l, studentLine.getAndIncrement())).filter(s->s.choices.size() > 0)
        		.collect(toMap(s->s.getName(), s->s, (a,b)->{
        			if (a.choices.size() < b.choices.size()) {
        				return b;
        			} else {
        				return a;
        			}
        		}));

        return students;
    }
    private void parse(InputStream classStream, InputStream rainClassStream, InputStream studentStream, InputStream existingScheduleStream, InputStream existingRainScheduleStream) {
        try(BufferedReader classFile = getReader(classStream);
        	BufferedReader rainClassFile = getReader(classStream);
            BufferedReader studentFile = getReader(studentStream);
            BufferedReader existingFile = getReader(existingScheduleStream);
            BufferedReader existingRainFile = getReader(existingRainScheduleStream)) {

            this.classes = parseClassFile(classFile);
            
            if (!rainClassFile.ready()) {
            	// if not rain schedule was specified, derive one
            	this.rainClasses = copyClasses(this.classes);
            	rainClasses.values().forEach(Class::clearForRainSchedule);
            } else {
            	rainClasses = parseClassFile(rainClassFile);
            }

            Map<String, Student> students = parseStudents(studentFile);
            Map<String, Student> rainStudents = new HashMap<>();
            
            // deep copy the students over to the rain status
            students.forEach((name,obj)->{
            	rainStudents.put(name, new Student(obj));
            });
            
            parseExistingStudents(classes, students, existingFile);
            boolean existingRainStudents = parseExistingStudents(rainClasses, rainStudents, existingRainFile);

            this.students = new ArrayList<>(students.values());
            this.rainStudents = existingRainStudents ? new ArrayList<>(rainStudents.values()) : null;
        } catch (Exception e) {
            e.printStackTrace(err);
        }
    }
    
    private void forceAddStudentsToClasses(Student s, Map<String, Class> classes) {
        if (s != null) {
            for (int i = 0; i < 6; i++) {
                String c = Optional.ofNullable(s.assignments.get(i)).map(a->a.name).orElse(null);
                
                // some first graders were accidentally assigned classes after before 4th period, clean these out.
                if (s.isInFirstGrade() && i < 3) {
                    continue;
                }
                
                if (StringUtils.isNotBlank(c)) {
                    Class clazz = classes.get(c);
                    if (clazz == null) {
                        err.println("Could not find class: " + c + " for " + s.getName());
                    } else {
                        Period p = clazz.getPeriod(i);
                        if (p != null && p.forceAddStudent(s)) {
                            s.assignments.put(i, new Assignment(c, true));
                        }
                    }
                }
            }
            s.removeChoicesThatAreAlreadyAssigned();
        } 
    	
    }
    
    public void everyoneGetsFirstChoice(List<Student> students, Map<String,Class> classes) {
        Collections.sort(students);
        int numPeriods = getNumPeriods(classes);
        
        for (int i = 0; i < numChoices; i++) {
            for(Student s: students) {
                int period = -1;
                int availablePeriods = s.isInFirstGrade() ? 3 : numPeriods;
                int startingPeriod = s.isInFirstGrade() ? 3 : 0;
                while (period == -1 && s.hasMoreChoices(startingPeriod, availablePeriods)) {
                    String className = s.getNextChoice();
                    Set<Integer> available = IntStream.range(startingPeriod, startingPeriod + availablePeriods).filter(p->!s.assignments.containsKey(p)).mapToObj(p->p).collect(toSet());
                    if (!available.isEmpty()) {
	                    Class c = classes.get(className);
	                    if (c == null || StringUtils.isBlank(className)) {
	                        err.println("Unknown Class: " + className);
	                    } else {
	                        period = c.addStudent(s, available);
	                    }
                    }
                }
                
//                if (period == -1) {
//                	System.out.println(s.getName() + " does not have all their classes.");
//                }
            }
        }
    }
    
    private int getMaxChoices(List<Student> students) {
    	return students.stream()
    			.mapToInt(s->s.choices.size())
    			.max()
    			.orElse(10);
    }
    
    private int getNumPeriods(Map<String, Class> classes) {
        return classes.values().stream().findFirst().map(c->c.periods.size()).orElse(0);
    }
    
    private Pair<ByteArrayOutputStream, ByteArrayOutputStream> outputResults(List<Student> students, Map<String, Class> classes) {
        int numPeriods = getNumPeriods(classes);
        int maxScore = 0;
        int firstGradeMaxScore = 0;
        for (int i = 0; i < numPeriods; i++) {
            maxScore += (10-i);
            if (i < 3) {
                firstGradeMaxScore += (10-i);
            }
        }
        
        String classHeader = IntStream.range(1, numPeriods+1)
                .mapToObj(i->"Session "+i)
                .flatMap(h->Stream.of(h, h+" Location"))
                .collect(joining(","));
        ByteArrayOutputStream assignmentsOut = new ByteArrayOutputStream();
        try (PrintWriter assignments = new PrintWriter(assignmentsOut)) {
            assignments.println("Last Name,First Name,Grade,Teacher,Happiness,NumClasses,"+classHeader);
            for(Student s: students) {
                assignments.println(s.toCsv(numPeriods, maxScore, firstGradeMaxScore, classes));
            }
        }
        
        
        ByteArrayOutputStream classSizesOut = new ByteArrayOutputStream();
        try (PrintWriter classSizes = new PrintWriter(classSizesOut)) {
            classSizes.println(classes.values().iterator().next().getCsvHeader());
            for(Class c: classes.values()) {
                classSizes.println(c.toCsv());
            }
        }
        
        return Pair.of(assignmentsOut, classSizesOut);
    }
    
    private List<Student> copyStudents(List<Student> s) {
        return s.stream().map(Student::new).collect(toList());
    }
    
    private Map<String, Class> copyClasses(Map<String, Class> c) {
        return c.entrySet().stream()
            .map(e->Pair.of(e.getKey(), new Class(e.getValue())))
            .collect(toMap(k->k.getLeft(), v->v.getRight(), (a,b)->a, TreeMap::new));
    }
    
    private Set<String> getClassesForPeriod(Map<String, Class> classes, int i) {
        return classes.values().stream()
            .filter(c->c.isAvailableForPeriod(i))
            .map(c->c.getName())
            .filter(StringUtils::isNotBlank)
            .collect(toSet());
    }
    
    private void rearrangeChoicesForMissingPeriods(List<Student> students) {
        int numPeriods = getNumPeriods(classes);
        students.forEach(student->{
            for (int i = 0; i < numPeriods; i++) {
                if (!student.assignments.containsKey(i)) {
                    Set<String> choices = new LinkedHashSet<>(student.choices);
                    Set<String> classes = getClassesForPeriod(this.classes, i);
                    choices.retainAll(classes);
                    choices.removeAll(student.assignments.values().stream().map(a->a.name).collect(toList()));
                    if (!choices.isEmpty()) {
                        student.choices.add(0, choices.iterator().next());
                        student.choices = new ArrayList<>(new LinkedHashSet<>(student.choices));
                    } else {
//                        System.out.println(student.getName() + " doesn't have any choices for session " + (i+1));
                    }
                }
            }
        });
    }
    
    private boolean firstGraderMissingClasses(Student s) {
    	return (s.isInFirstGrade() && s.assignments.size() < 3);
    }
    
    private boolean studentMissingClasses(Student s, int numPeriods) {
    	return firstGraderMissingClasses(s) || (!s.isInFirstGrade() && s.assignments.size() < numPeriods);
    }
    
    private Stream<Student> streamStudentsWithoutAllClasses(List<Student> s, Map<String, Class> classes) {
    	int numPeriods = getNumPeriods(classes);
        return s.stream().filter(student->studentMissingClasses(student, numPeriods));
    }
    
    public Schedule run() {
    	err.println("Running Normal Schedule");
    	err.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
    	return run(classes, students, false);
    }
    
    public Schedule run(Map<String, Class> classes, List<Student> students, boolean isRaining) {
        students = copyStudents(students);
        classes = copyClasses(classes);
        
        if (isRaining) {
        	err.println("\nRunning Rain Schedule");
        	err.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        	
        	Set<String> nonRainClasses = classes.entrySet().stream()
        			.filter(e->!e.getValue().isCancelledWhenRaining)
        			.map(e->e.getKey())
        			.collect(toSet());
        	
        	for (Student student : students) {
        		student.lockNonRainAssignments(nonRainClasses);
        		student.resetAssignment();
        		forceAddStudentsToClasses(student, classes);
        	}
        }

        Map<String, Class> c = copyClasses(classes);
        List<Student> s = copyStudents(students);
        
        Pair<List<Student>, Map<String,Class>> bestRun = Pair.of(null, null);
        int bestMissing = Integer.MAX_VALUE;
        int numStudentsMissingClasses = -1;
        int tries = 100;
        
        
        while (tries-- > 0) {
            everyoneGetsFirstChoice(s, c);
            
            List<Student> studentsWithoutAllClasses = streamStudentsWithoutAllClasses(s, c).collect(toList());

            numStudentsMissingClasses = studentsWithoutAllClasses.size();
            
            if (numStudentsMissingClasses < bestMissing) {
                bestMissing = numStudentsMissingClasses;
                bestRun = Pair.of(copyStudents(s), copyClasses(c));
            }
            
            // do something to make it so that more students get their classes filled.
            rearrangeChoicesForMissingPeriods(studentsWithoutAllClasses);
            
            c = copyClasses(classes);
            c.values().stream().forEach(Class::resetAssignment);
            s.stream().forEach(Student::resetAssignment);
        }
        

        
        long studentsWithoutFullSchedule = streamStudentsWithoutAllClasses(bestRun.getLeft(), bestRun.getRight())
        		//.peek(student->System.err.println(student.getName() + " doesn't have a full schedule."))
        		.count();
        
        if (randomlyFillMissingClasses) {
        	fillInHolesInClassAssignments(bestRun.getLeft(), bestRun.getRight());
        }
        err.println(studentsWithoutFullSchedule + " students don't have full schedules" + (randomlyFillMissingClasses ? ", have assigned random classes." : "."));
        Pair<ByteArrayOutputStream, ByteArrayOutputStream> output = outputResults(bestRun.getLeft(), bestRun.getRight());
        
        byte[] rainAssignments = null;
        byte[] rainClassSizes = null;
        if (!isRaining) {
        	Schedule rainSchedule = run(rainClasses, rainStudents != null ? rainStudents : bestRun.getLeft(), true);
        	rainAssignments = rainSchedule.getAssignments();
        	rainClassSizes = rainSchedule.getClassSizes();
        }
        
        err.close();
        return new Schedule(output.getLeft().toByteArray(), output.getRight().toByteArray() ,rainAssignments, rainClassSizes, studentsWithoutFullSchedule, new String(errStream.toByteArray(), Charset.forName("utf8")));
    }
    
    private void fillInHolesInClassAssignments(List<Student> students, Map<String, Class> classes) {
        Random r = new Random();
        streamStudentsWithoutAllClasses(students, classes).forEach(student->{
        	int start = student.isInFirstGrade() ? 3 : 0;
            for (int i = start; i < 6; i++) {
                if (!student.assignments.containsKey(i)) {
                    List<String> potentialClasses = new ArrayList<>(getClassesForPeriod(classes, i));
                    student.assignments.values().stream().map(a->a.name).forEach(potentialClasses::remove);
                    if (!potentialClasses.isEmpty()) {
	                    Class randomClass = classes.get(potentialClasses.get(r.nextInt(potentialClasses.size())));
	                    if (randomClass.getPeriod(i).addStudent(student)) {
	                    	err.println("Randomly adding " + student.getName() + " to class " + randomClass.name);
	                        student.assignments.put(i, new Assignment(randomClass.name, false));
	                    } else {
	                        err.println("Tried to add " + student.getName() + " to " + randomClass.name + " but it's full?");
	                    }
                    } else {
                    	err.println("Out of classes for period " + (i+1) + " for " + student.getName());
                    }
                }
            }
        });
    }
    
    public static void main(String[] args) throws Exception {
    	try (
			InputStream classFile = new FileInputStream("/Users/ben/Documents/Explore More Day 2019/classes.csv");
    		InputStream studentsFile = new FileInputStream("/Users/ben/Documents/Explore More Day 2019/students.csv");
			InputStream rainClassFile = new FileInputStream("/Users/ben/Documents/Explore More Day 2019/classes-rain.csv");
    	) {
			Scheduling scheduler = new Scheduling(classFile, rainClassFile, studentsFile, null, null, false);
			Schedule s = scheduler.run();
			System.err.println(s.getMsg());
			
			try (
				OutputStream assignmentsFile = new FileOutputStream("/Users/ben/Documents/Explore More Day 2019/assignments.csv");
				OutputStream rainAssignmentsFile = new FileOutputStream("/Users/ben/Documents/Explore More Day 2019/rain-assignments.csv");
				OutputStream classSizes = new FileOutputStream("/Users/ben/Documents/Explore More Day 2019/class-sizes.csv");
				OutputStream rainClassSizes = new FileOutputStream("/Users/ben/Documents/Explore More Day 2019/rain-class-sizes.csv");
			) {
				IOUtils.copy(new ByteArrayInputStream(s.getAssignments()), assignmentsFile);
				IOUtils.copy(new ByteArrayInputStream(s.getRainAssignments()), rainAssignmentsFile);
				IOUtils.copy(new ByteArrayInputStream(s.getClassSizes()), classSizes);
				IOUtils.copy(new ByteArrayInputStream(s.getRainClassSizes()), rainClassSizes);
			}
    	}
	}
}
