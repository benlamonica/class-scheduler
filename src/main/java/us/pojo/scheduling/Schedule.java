package us.pojo.scheduling;

public class Schedule {
	private byte[] assignments;
	private byte[] rainAssignments;
	private byte[] classSizes;
	private byte[] rainClassSizes;
	private long studentsMissingAssignments;
	private String msg;
	
	public Schedule(byte[] assignments, byte[] classSizes, byte[] rainAssignments, byte[] rainClassSizes, long studentsMissingAssignments, String msg) {
		this.assignments = assignments;
		this.classSizes = classSizes;
		this.rainAssignments = rainAssignments;
		this.rainClassSizes = rainClassSizes;
		this.studentsMissingAssignments = studentsMissingAssignments;
		this.msg = msg;
	}
	
	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
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

	public byte[] getRainClassSizes() {
		return rainClassSizes;
	}

	public byte[] getRainAssignments() {
		return rainAssignments;
	}
}
