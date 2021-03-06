package org.usfirst.frc.team5806.robot;

import java.util.Comparator;

import java.util.Vector;

import com.ni.vision.NIVision;
import com.ni.vision.NIVision.DrawMode;
import com.ni.vision.NIVision.Image;
import com.ni.vision.NIVision.ImageType;
import com.ni.vision.NIVision.ShapeMode;

import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.networktables.NetworkTable;
import edu.wpi.first.wpilibj.vision.USBCamera;

/**
 * Analyzes targets and finds the best one. Based on the 2015 Vision Color Sample,
 * and https://wpilib.screenstepslive.com/s/3120/m/8731/l/91395-c-java-code
 * Also defines methods which can be used to display target information on
 * SmartDashboard and provide functionality to subsystems using the camera.
 */
public class TargetTracker {	
	// CONSTANTS
	// target geometry
	final double IDEAL_ASPECT_RATIO = 20.0 / 14.0;
	final double IDEAL_AREA_RATIO =  88.0 / 280.0;
	final double TARGET_WIDTH = 20 / 12.0;
	final double TARGET_HEIGHT = 14 / 12.0;
	// camera/image properties
	final double CAMERA_FOV = 39.935; // VERTICAL (By measuring distance from a known size object that spans vertical FOV and using tan OR solving the equation in getRangeToBestTarget for FOV using other measurements from debug)
	final double CAMERA_ELEVATION = 45; // degrees
	// threshold values
	NIVision.Range HUE_RANGE = new NIVision.Range(60, 150);	//Default hue range for target
	NIVision.Range SAT_RANGE = new NIVision.Range(60, 255);	//Default saturation range for target
	NIVision.Range VAL_RANGE = new NIVision.Range(100, 255);	//Default value range for target
	// general scoring
	final double AREA_MINIMUM = 1; // Default Area minimum for particle as percentage of total area (pixels are hard with NIVision)
	final double AREA_MAXIMUM = 100.0; // Max area by same measure
	// private final double MIN_SCORE = 75;
	NIVision.ParticleFilterCriteria2 areaFilterCritera[] = new NIVision.ParticleFilterCriteria2[1];
	NIVision.ParticleFilterOptions2 filterOptions = new NIVision.ParticleFilterOptions2(0,0,1,1);
	
	// target crosshair display constants
	final int TARGET_CROSSHAIR_SIZE = 20; // length from center
	final int TARGET_CROSSHAIR_WIDTH = 5;
	final int TARGET_CROSSHAIR_SPREAD = 5; // spread from center
	
	// Various cache variables
	private ParticleReport currentBestTarget = null;
	private ParticleReport[] allTargets = null;
	public double currentRangeToBestTarget = 0;
	
	// A structure to hold measurements of a particle
	public class ParticleReport implements Comparator<ParticleReport>, Comparable<ParticleReport>{
		double PercentAreaToImageArea;
		double Area;
		double centerX;
		double centerY;
		double BoundingRectLeft;
		double BoundingRectTop;
		double BoundingRectRight;
		double BoundingRectBottom;
		double AspectRatioScore;
		double AreaRatioScore;
		
		public int compareTo(ParticleReport r)
		{
			return (int)(r.Area - this.Area);
		}
		
		public int compare(ParticleReport r1, ParticleReport r2)
		{
			return (int)(r1.Area - r2.Area);
		}
	};

	// Structure to represent the scores for the various tests used for target identification
	public class Scores {
		double aspectRatioScore;
		double areaRatioScore;
	};

	// Images
	USBCamera camera;
	Image frame;
	Image binaryFrame;
	
	public TargetTracker()
	{
		camera = new USBCamera("cam0");
		camera.openCamera();
		camera.startCapture();
		
		System.out.println("Bool first: " + (camera == null));
		frame = NIVision.imaqCreateImage(ImageType.IMAGE_RGB, 0);
		binaryFrame = NIVision.imaqCreateImage(ImageType.IMAGE_U8, 0);
		areaFilterCritera[0] = new NIVision.ParticleFilterCriteria2(
				NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA, AREA_MINIMUM, AREA_MAXIMUM, 0, 0);
	}
	
	public void showImage() {
		camera.getImage(frame);
		CameraServer.getInstance().setImage(frame);
	};
	
	/**
	 * Generates complete scores for all found targets,
	 * then deduces the best. Sets this class's best target
	 * reference to the one found
	 * @return The best target's ParticleReport
	 */
	public ParticleReport retrieveBestTarget()
	{
//		// make sure we have an image
		if (frame != null && binaryFrame != null)
		{			
			// get images (DO NOT GET binaryFrame, its created by the threshold operation below)
			camera.getImage(frame);
			
			// threshold based on HSV
			NIVision.imaqColorThreshold(binaryFrame, frame, 255, NIVision.ColorMode.HSV, HUE_RANGE, SAT_RANGE, VAL_RANGE);
			
			
			// filter out small particles
			areaFilterCritera[0].lower = (float) AREA_MINIMUM;
			areaFilterCritera[0].upper = (float) AREA_MAXIMUM;
			int imaqError = NIVision.imaqParticleFilter4(binaryFrame, binaryFrame, areaFilterCritera, filterOptions, null);
			
			// get number of particles after filter
			int numParticles = NIVision.imaqCountParticles(binaryFrame, 1);
			
			if (numParticles > 0)
			{		
				//Measure particles and sort by particle size
				Vector<ParticleReport> particles = new Vector<ParticleReport>();
				for(int particleIndex = 0; particleIndex < numParticles; particleIndex++)
				{
					// create a new particle report, as defined above, and populate
					// it with the relevant information about the particle
					ParticleReport par = new ParticleReport();
					par.PercentAreaToImageArea = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA);
					par.Area = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA);
					par.BoundingRectTop = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_TOP);
					par.BoundingRectLeft = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_LEFT);
					par.BoundingRectBottom = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_BOTTOM);
					par.BoundingRectRight = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_RIGHT);
					// based on bounding box, find center (add half width to lowest edge value)
					par.centerX = par.BoundingRectLeft  + (par.BoundingRectRight - par.BoundingRectLeft)/2;
					par.centerY = par.BoundingRectTop + (par.BoundingRectBottom - par.BoundingRectTop)/2;
					// score each target using arbitrary functions I made (to see which are most like the target)
					par.AspectRatioScore = calculateAspectRatioScore(par);
					par.AreaRatioScore = calculateAreaRatioScore(par);
					particles.add(par);
				}
				particles.sort(null);
				
				// then, find the best target based on its score
				ParticleReport bestTarget = null;
				double bestScore = 0;
				
				for (ParticleReport particle : particles)
				{
					if (getCumulativeScore(particle) > bestScore)
					{
						bestTarget = particle;
						bestScore = getCumulativeScore(particle);
					}	
					
					drawBoundingBox(particle, frame);
				}
				
				// draw indicator at best target
				if (bestTarget != null)
					drawTargetIndicator((int)bestTarget.centerX, (int)bestTarget.centerY, frame);
				
				CameraServer.getInstance().setImage(frame);
				currentRangeToBestTarget = getRangeToBestTarget();
				currentBestTarget = bestTarget;
				return bestTarget;
			}
		}
		camera.getImage(frame);
		System.out.println("Bool second: " + (frame == null));
		CameraServer.getInstance().setImage(frame);
		
		return null;
	}

	/**
	 * Calculates a 0-1 aspect ratio score of the given target,
	 * based off the difference between it's ratio and the ideal one.	
	 */
	private double calculateAspectRatioScore(ParticleReport report) {
		double height = report.BoundingRectRight - report.BoundingRectLeft;
		double width = report.BoundingRectBottom - report.BoundingRectTop;
		
		// ensure no error
		if (height != 0)
			return scoreFromDistance(width / height, IDEAL_ASPECT_RATIO);
		else
			return 0;
	}
	
	/**
	 * Generates cumulative score for the current target.
	 * Must have setAdditionalScores run beforehand
	 */
	private double getCumulativeScore(ParticleReport report)
	{
		// if both scores are PERFECT, this will give a one
		return (report.AspectRatioScore + report.AreaRatioScore) / 2.0;
	}

	/**
	 * Calculates a 0-1 area score of the given target,
	 * based off the difference between the ideal ratio 
	 * of the bounding box's area to the internal area AND the actual.
	 */
	private double calculateAreaRatioScore(ParticleReport report) {
		double boundingBoxArea = (report.BoundingRectBottom - report.BoundingRectTop) * 
								(report.BoundingRectRight - report.BoundingRectLeft);
		
		// prevent error when there is zero area
		if (boundingBoxArea != 0)
			return scoreFromDistance(report.Area / boundingBoxArea, IDEAL_AREA_RATIO);
		else
			return 0;
	}
	
	/**
	 * Calculates a score representing the distance of a value off the ideal value.
	 * Uses a peicewise "pyramid" function which is highest (1) at one but falls off to 0
	 * as the ratio approaches 0 or 2.
	 * @param realValue The current, actual value
	 * @param idealValue The optimal value (would result in highest score)
	 * @return A 0-1 value representing the "closeness" of the realValue to the idealValue
	 */
	private double scoreFromDistance(double realValue, double idealValue)
	{
		// Create a "pyramid function", inverting an absolute value function and 
		// shifting so a 0 in the difference between one and the ratio of the values
		// results in a 1 on the score
		if (idealValue != 0)
			return Math.max(0, Math.min(1 - Math.abs(1 - realValue/idealValue), 1));
		else
			return 0;
	}
	

	/**
	 * Finds the distance of the current target from the center of the camera view
	 * @return A two part array [x, y] representing the x and y distances 
	 * normalized to be [+/-1, +/-1] at edges of image
	 * (+x is to the right, +y is up)
	 */
	public double[] getTargetDistanceFromCenter()
	{
		// watch for no valid target, in which case give no displacement
		double[] distance = new double[2];
		if (currentBestTarget != null && frame != null)
		{
			NIVision.GetImageSizeResult size = NIVision.imaqGetImageSize(frame);
			
			// x increases to right, but y increased downwards, so invert y
			distance[0] =  (currentBestTarget.centerX - size.width/2.0 ) / (size.width/2);
			distance[1] = -(currentBestTarget.centerY - size.height/2.0) / (size.height/2);
		}
		return distance;
	}
	
	/**
	 * Finds the target's horizontal distance to the target in feet (or whatever measure TARGET_WIDTH is in)
	 * @return The distance, or 0 if no target is found. Most accurate if head on to target
	 * Based on https://wpilib.screenstepslive.com/s/3120/m/8731/l/90361-identifying-and-processing-the-targets
	 * and example code in 2015 Vision Retro Sample (they do it slightly differently with variables, but its the same)
	 */
	private double getRangeToBestTarget()
	{
		if (currentBestTarget != null && frame != null)
		{
			// get image size from binary frame
			NIVision.GetImageSizeResult size = NIVision.imaqGetImageSize(frame);
			double targetHeightPixel = currentBestTarget.BoundingRectBottom - currentBestTarget.BoundingRectTop;
			
			// chose to use height because most consistent across view angles
			// d = TargetHeightFeet*FOVHeightPixel / (2*TargetHeightPixel*tan(FOV/2) ) (HYPOTENUSE)
			return TARGET_HEIGHT*size.height / (2*targetHeightPixel*Math.tan(Math.toRadians(CAMERA_FOV/2)))
					*Math.cos(Math.toRadians(CAMERA_ELEVATION)); // convert to horizontal
		}
		else
			return 0;
	}
	
	/**
	 * Draw a crosshair indicator at the given position
	 */
	public void drawTargetIndicator(int x, int y, Image frame)
	{    
		if (frame != null)
		{
	  		// draw four rectangles for crosshairs (shift so x,y is at center)
	      	NIVision.Rect[] crosshairRects = new NIVision.Rect[4];
	      	// right
	  		crosshairRects[0] = new NIVision.Rect(y - TARGET_CROSSHAIR_WIDTH/2, x + TARGET_CROSSHAIR_SPREAD,
	  											TARGET_CROSSHAIR_WIDTH, TARGET_CROSSHAIR_SIZE);
	  		// left
	  		crosshairRects[1] = new NIVision.Rect(y - TARGET_CROSSHAIR_WIDTH/2, x - TARGET_CROSSHAIR_SPREAD - TARGET_CROSSHAIR_SIZE,  
	  											TARGET_CROSSHAIR_WIDTH, TARGET_CROSSHAIR_SIZE);
	  		// top
	  		crosshairRects[2] = new NIVision.Rect(y - TARGET_CROSSHAIR_SPREAD - TARGET_CROSSHAIR_SIZE, x - TARGET_CROSSHAIR_WIDTH/2,  
	  										  	TARGET_CROSSHAIR_SIZE, TARGET_CROSSHAIR_WIDTH);
	  		// bottom
	  		crosshairRects[3] = new NIVision.Rect(y + TARGET_CROSSHAIR_SPREAD, x - TARGET_CROSSHAIR_WIDTH/2,
	  											TARGET_CROSSHAIR_SIZE, TARGET_CROSSHAIR_WIDTH);
	  
	  		// add shapes to frame
	  		for (NIVision.Rect crosshairRect : crosshairRects)
	  			NIVision.imaqDrawShapeOnImage(frame, frame, crosshairRect, DrawMode.PAINT_VALUE, ShapeMode.SHAPE_RECT, 255.0f);
		}
    }
	
	private void drawBoundingBox(ParticleReport particle, Image frame)
	{
		NIVision.Rect boundingBox = new NIVision.Rect((int) particle.BoundingRectTop, (int) particle.BoundingRectLeft, 
												   	  (int) (particle.BoundingRectBottom - particle.BoundingRectTop),
												   	  (int) (particle.BoundingRectRight - particle.BoundingRectLeft));
		
		NIVision.imaqDrawShapeOnImage(frame, frame, boundingBox, DrawMode.DRAW_VALUE, ShapeMode.SHAPE_RECT, 255.0f);
	}
}
