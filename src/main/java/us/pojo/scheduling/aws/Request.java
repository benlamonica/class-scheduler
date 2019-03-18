package us.pojo.scheduling.aws;

public class Request {

	private String classSchedule;
	private String rainClassSchedule;
	private String students;
	private String existingAssignments;

	public String getClassSchedule() {
		return classSchedule;
	}

	public void setClassSchedule(String classSchedule) {
		this.classSchedule = classSchedule;
	}

	public String getStudents() {
		return students;
	}

	public void setStudents(String students) {
		this.students = students;
	}

	public String getRainClassSchedule() {
		return rainClassSchedule;
	}

	public void setRainClassSchedule(String rainClassSchedule) {
		this.rainClassSchedule = rainClassSchedule;
	}

	public String getExistingAssignments() {
		return existingAssignments;
	}

	public void setExistingAssignments(String existingAssignments) {
		this.existingAssignments = existingAssignments;
	}
}
