package us.pojo.scheduling;

import java.io.ByteArrayOutputStream;

public class Schedule {
	private ByteArrayOutputStream assignments;
	private ByteArrayOutputStream classSizes;
	private long studentsMissingAssignments;
	
	public Schedule(ByteArrayOutputStream assignments, ByteArrayOutputStream classSizes, long studentsMissingAssignments) {
		this.assignments = assignments;
		this.classSizes = classSizes;
		this.studentsMissingAssignments = studentsMissingAssignments;
	}

	public ByteArrayOutputStream getAssignments() {
		return assignments;
	}

	public ByteArrayOutputStream getClassSizes() {
		return classSizes;
	}

	public long getStudentsMissingAssignments() {
		return studentsMissingAssignments;
	}
}
