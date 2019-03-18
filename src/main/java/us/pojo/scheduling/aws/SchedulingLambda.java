package us.pojo.scheduling.aws;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import us.pojo.scheduling.Schedule;
import us.pojo.scheduling.Scheduling;

	
public class SchedulingLambda {

	private static final String BUCKET = "class-scheduler-output";
	private AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
	
	private ByteArrayInputStream getStream(String base64) {
		if (base64 != null) {
			return new ByteArrayInputStream(Base64.getDecoder().decode(base64));
		} else {
			return new ByteArrayInputStream(new byte[0]);
		}
	}
	
	public LambdaResponse scheduleStudents(Request request, Context context) {
		context.getLogger().log("Handling a message! " + request.toString());
		Scheduling scheduling = new Scheduling(getStream(request.getClassSchedule()), getStream(request.getStudents()), getStream(request.getExistingAssignments()));
		Schedule s = scheduling.run();
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Chicago"));
		String prefix = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-");
		ObjectMetadata meta = new ObjectMetadata();
		meta.setContentType("text/csv");
		
		s3.putObject(BUCKET, prefix+"/assignments.csv", new ByteArrayInputStream(s.getAssignments().toByteArray()), meta);
		s3.putObject(BUCKET, prefix+"/class-sizes.csv", new ByteArrayInputStream(s.getClassSizes().toByteArray()), meta);
		URL assignmentsUrl = s3.generatePresignedUrl(BUCKET, prefix+"/assignments.csv", new Date(System.currentTimeMillis() + (24*60*60*1000)));
		URL classSizesUrl = s3.generatePresignedUrl(BUCKET, prefix+"/class-sizes.csv", new Date(System.currentTimeMillis() + (24*60*60*1000)));
		
//		scheduling = new Scheduling()
//		s3.putObject("class-scheduler-output", prefix+"/rain-assignments.csv", new ByteArrayInputStream(s.getAssignments().toByteArray()), meta);
//		s3.putObject("class-scheduler-output", prefix+"/rain-class-sizes.csv", new ByteArrayInputStream(s.getAssignments().toByteArray()), meta);
//		scheduling.run
//		s3.generatePresignedUrl(bucketName, key, expiration)

		return new LambdaResponse(new Result("Success", assignmentsUrl.toString(), classSizesUrl.toString(), s.getStudentsMissingAssignments()));
//		return new Result("test", "http://assignments", "http://classes", 0);
	}
}
