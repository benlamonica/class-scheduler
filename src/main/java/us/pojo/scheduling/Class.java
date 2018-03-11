package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static us.pojo.scheduling.CSVParser.parseLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.validator.routines.IntegerValidator;

public class Class {
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
    
    public String getName() {
        return name;
    }

    public boolean isAvailableForPeriod(int i) {
        return periods.get(i).maxStudents > 0;
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
