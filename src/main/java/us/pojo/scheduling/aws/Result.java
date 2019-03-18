package us.pojo.scheduling.aws;

public class Result {
	private String message;
	private String assignmentsUrl;
	private String classSizesUrl;
	private long studentsMissingAssignments;

	public Result(String message, String assignmentsUrl, String classSizesUrl, long studentsMissingAssignments) {
		this.message = message;
		this.assignmentsUrl = assignmentsUrl;
		this.classSizesUrl = classSizesUrl;
		this.studentsMissingAssignments = studentsMissingAssignments;
	}

	public String getAssignmentsUrl() {
		return assignmentsUrl;
	}

	public void setAssignmentsUrl(String assignmentsUrl) {
		this.assignmentsUrl = assignmentsUrl;
	}

	public String getClassSizesUrl() {
		return classSizesUrl;
	}

	public void setClassSizesUrl(String classSizesUrl) {
		this.classSizesUrl = classSizesUrl;
	}

	public long getStudentsMissingAssignments() {
		return studentsMissingAssignments;
	}

	public void setStudentsMissingAssignments(long studentsMissingAssignments) {
		this.studentsMissingAssignments = studentsMissingAssignments;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
