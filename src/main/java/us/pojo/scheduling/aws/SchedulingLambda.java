package us.pojo.scheduling.aws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import us.pojo.scheduling.Schedule;
import us.pojo.scheduling.Scheduling;

	
public class SchedulingLambda {

	private static final String BUCKET = "class-scheduler";
	private AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
	
	private ByteArrayInputStream getStream(String base64) {
		if (base64 != null) {
			return new ByteArrayInputStream(Base64.getDecoder().decode(base64));
		} else {
			return new ByteArrayInputStream(new byte[0]);
		}
	}
	
	private void addFile(ZipOutputStream zip, String filename, byte[] val) throws IOException {
		zip.putNextEntry(new ZipEntry(filename));
		zip.write(val);
	}
	
	public LambdaResponse scheduleStudents(Request request, Context context) {
		context.getLogger().log("Handling a message! " + request.toString());
		Scheduling scheduling = new Scheduling(
				getStream(request.getClassSchedule()), 
				getStream(request.getRainClassSchedule()), 
				getStream(request.getStudents()), 
				getStream(request.getExistingAssignments()), 
				getStream(request.getExistingRainAssignments()),
				false);
		context.getLogger().log("Data Loaded. Commencing run.");
		Schedule s = scheduling.run();
		context.getLogger().log("Run completed, zipping results.");
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Chicago"));
		String prefix = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-");
		ObjectMetadata meta = new ObjectMetadata();
		meta.setContentType("application/zip");

		ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
			addFile(zip, "assignments.csv", s.getAssignments());
			addFile(zip, "class-sizes.csv", s.getClassSizes());
			addFile(zip, "rain-assignments.csv", s.getRainAssignments());
			addFile(zip, "rain-class-sizes.csv", s.getRainClassSizes());
			addFile(zip, "classes.csv", Base64.getDecoder().decode(request.getClassSchedule()));
			addFile(zip, "rain-classes.csv", Base64.getDecoder().decode(request.getRainClassSchedule()));
			addFile(zip, "students.csv", Base64.getDecoder().decode(request.getStudents()));
			zip.close();
		} catch (Exception e) {
			context.getLogger().log("Exception while zipping.");
		}
		
		String key = "results/schedule-"+prefix+".zip";
		s3.putObject(BUCKET, key, new ByteArrayInputStream(zipBytes.toByteArray()), meta);
		context.getLogger().log("Pre-signing url.");
		URL zipUrl = s3.generatePresignedUrl(BUCKET, key, new Date(System.currentTimeMillis() + (24*60*60*1000)));

		context.getLogger().log("Url: " + zipUrl.toString());

		return new LambdaResponse(new Result(s.getMsg(), zipUrl.toString(), s.getStudentsMissingAssignments()));
	}
}
