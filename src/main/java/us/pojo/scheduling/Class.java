package us.pojo.scheduling;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static us.pojo.scheduling.CSVParser.parseLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.validator.routines.IntegerValidator;

public class Class {
    public static class Period {
        public static class PeriodAssignment {
            public PeriodAssignment(Student s, boolean locked) {
                this.s = s;
                this.locked = locked;
            }
            Student s;
            boolean locked = false;
        }
        public int maxStudents;
        public List<PeriodAssignment> students = new ArrayList<>();
        public Period(Period copy) {
            this(copy.maxStudents);
            students = new ArrayList<>(copy.students);
        }
        public Period(Integer maxStudents) {
            this.maxStudents = maxStudents == null ? 0 : maxStudents;
        }
        public boolean addStudent(Student s) {
            if (students.size() < maxStudents) {
                students.add(new PeriodAssignment(s, false));
                return true;
            }
            return false;
        }
        public boolean forceAddStudent(Student s) {
            if (students.size() < maxStudents) {
                students.add(new PeriodAssignment(s, true));
                return true;
            }
            return false;
        }
        public void clear() {
            students = students.stream().filter(a->a.locked).collect(toList());
        }
    }

    public List<Period> periods;
    public String name;
    public String location;
    public int minGrade;
    public boolean isCancelledWhenRaining;
    
    public Class(Class copy) {
        this.name = copy.name;
        this.periods = copy.periods.stream().map(Period::new).collect(toList());
        this.minGrade = copy.minGrade;
        this.location = copy.location;
    }
    
    public String getName() {
        return name;
    }

    public boolean isAvailableForPeriod(int i) {
        return periods.get(i).maxStudents > 0 && periods.get(i).students.size() < periods.get(i).maxStudents;
    }
    
    public void resetAssignment() {
        periods.stream().forEach(p->p.clear());
    }
    
    public Class(String line, Map<String, Integer> header) {
        List<String> fields = parseLine(line);
        IntegerValidator intValidator = IntegerValidator.getInstance();
        name = fields.get(header.get("class name"));

        periods = IntStream.range(1, 10)
        	.mapToObj(i->"session " + i)
        	.filter(header::containsKey)
        	.map(i->fields.get(header.get(i)))
        	.map(intValidator::validate)
        	.map(Period::new)
        	.collect(toList());
        
        minGrade = Optional.ofNullable(fields.get(header.get("mingrade")))
        		.map(intValidator::validate)
        		.orElse(1);
        location = fields.get(header.get("location"));
        isCancelledWhenRaining = "1".equals(fields.get(header.get("iscancelledwhenraining")));
    }

    public int addStudent(Student s, Set<Integer> availablePeriods) {
        for (int period : availablePeriods) {
            if (periods.get(period).addStudent(s)) {
                return period;
            }
        }
        return -1;
    }
    
    public Period getPeriod(int i) {
        return periods.get(i);
    }
    
    public String getCsvHeader() {
        return "Name,"+IntStream.range(1, periods.size()+1).mapToObj(p->"Session " + p).collect(joining(","))+",MinGrade";
    }
    
    public String toCsv() {
        return "\""+name+"\","+periods.stream().map(p->String.valueOf(p.students.size())).collect(joining(","))+","+minGrade;
    }

    public String getLocation() {
        return location;
    }
}
