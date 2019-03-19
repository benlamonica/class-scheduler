package us.pojo.scheduling;

import java.io.ByteArrayOutputStream;

public class Schedule {
	private byte[] assignments;
	private byte[] classSizes;
	private long studentsMissingAssignments;
	
	public Schedule(ByteArrayOutputStream assignments, ByteArrayOutputStream classSizes, long studentsMissingAssignments) {
		this.assignments = assignments.toByteArray();
		this.classSizes = classSizes.toByteArray();
		this.studentsMissingAssignments = studentsMissingAssignments;
	}

	public byte[] getAssignments() {
		return assignments;
	}

	public byte[] getClassSizes() {
		return classSizes;
	}

	public long getStudentsMissingAssignments() {
		return studentsMissingAssignments;
	}
}
