package org.bioimageanalysis.icy.deeplearning.transformations;

import org.bioimageanalysis.icy.deeplearning.tensor.RaiArrayUtils;
import org.bioimageanalysis.icy.deeplearning.tensor.Tensor;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ScaleRangeTransformation extends AbstractTensorTransformation
{

	private static final String name = "scale_range";
	private double minPercentile = 0;
	private double maxPercentile = 1;
	private String axes;
	private Mode mode = Mode.PER_SAMPLE;
	private String tensorName;
	private float eps = (float) Math.pow(10, -6);
	
	public ScaleRangeTransformation()
	{
		super( name );
	}
	
	public void setMinPercentile(double minPercentile) {
		this.minPercentile = minPercentile / 100;
	}
	
	public void setMaxPercentile(double maxPercentile) {
		this.maxPercentile = maxPercentile / 100;
	}
	
	public void setAxes(String axes) {
		this.axes = axes;
	}
	
	public void setTensorName(String tensorName) {
		this.tensorName = tensorName;
	}
	
	public void setMode(String mode) {
		this.mode = Mode.valueOf(mode.toUpperCase());
	}
	
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	@Override
	public < R extends RealType< R > & NativeType< R > > Tensor< FloatType > apply( final Tensor< R > input )
	{
		final Tensor< FloatType > output = makeOutput( input );
		applyInPlace(output);
		return output;
	}

	@Override
	public void applyInPlace(Tensor<FloatType> input) {
		String selectedAxes = "";
		for (String ax : input.getAxesOrderString().split("")) {
			if (axes != null && !axes.toLowerCase().contains(ax.toLowerCase())
					&& !ax.toLowerCase().equals("b"))
				selectedAxes += ax;
		}
		if (axes == null || selectedAxes.equals("") 
				|| input.getAxesOrderString().replace("b", "").length() == selectedAxes.length()) {
			globalScale(input);
		} else if (axes.length() <= 2 && axes.length() > 0) {
			axesScale(input, selectedAxes);
		} else {
			//TODO allow scaling of more complex structures
			throw new IllegalArgumentException("At the moment, only allowed scaling of planes.");
		}
		
	}
	
	private void globalScale( final Tensor< FloatType > output ) {
		float minPercentileVal = findPercentileValue(output.getData(), minPercentile);
		float maxPercentileVal = findPercentileValue(output.getData(), maxPercentile);
		LoopBuilder.setImages( output.getData() )
				.multiThreaded()
				.forEachPixel( i -> i.set( ( i.get() - minPercentileVal ) / ( maxPercentileVal - minPercentileVal + eps ) ) );
	}
	
	private float findPercentileValue(RandomAccessibleInterval<FloatType> rai, double percentile) {
		double[] tmpArray = RaiArrayUtils.convertFloatArrIntoDoubleArr(RaiArrayUtils.floatArray(rai));
		double max = Util.max(tmpArray);
		double min = Util.min(tmpArray);
		return (float) ((max - min) * percentile + min);
	}
	
	private void axesScale( final Tensor< FloatType > output, String axesOfInterest) {
		long[] start = new long[output.getData().numDimensions()];
		long[] dims = output.getData().dimensionsAsLongArray();
		long[] indOfDims = new long[axesOfInterest.length()];
		long[] sizeOfDims = new long[axesOfInterest.length()];
		for (int i = 0; i < indOfDims.length; i ++) {
			indOfDims[i] = output.getAxesOrderString().indexOf(axesOfInterest.split("")[i]);
		}
		for (int i = 0; i < sizeOfDims.length; i ++) {
			sizeOfDims[i] = dims[(int) indOfDims[i]];
		}
		
		long[][] points = getAllCombinations(sizeOfDims);
		int c = 0;
		for (long[] pp : points) {
			for (int i = 0; i < pp.length; i ++) {
				start[(int) indOfDims[i]] = pp[i];
				dims[(int) indOfDims[i]] = pp[i] + 1;
			}
			// Define the view by defining the length per axis
			long[] end = new long[dims.length];
			for (int i = 0; i < dims.length; i ++) end[i] = dims[i] - start[i];
			IntervalView<FloatType> plane = Views.offsetInterval( output.getData(), start, end );
			float minPercentileVal = findPercentileValue(plane, minPercentile);
			float maxPercentileVal = findPercentileValue(plane, maxPercentile);
			LoopBuilder.setImages( plane )
					.multiThreaded()
					.forEachPixel( i -> i.set( ( i.get() - minPercentileVal ) / ( maxPercentileVal - minPercentileVal  + eps) ) );
		}
	}
	
	private static long[][] getAllCombinations(long[] arr){
		long n = 1;
		for (long nn : arr) n *= nn;
		long[][] allPoints = new long[(int) n][arr.length];
		for (int i = 0; i < n; i ++) {
			for (int j = 0; j < arr.length; j ++) {
				int factor = 1;
				for (int k = 0; k < j; k ++) {
					factor *= arr[k];
				}
				int auxVal = i / factor;
				int val = auxVal % ((int) arr[j]);
				allPoints[i][j] = val;
			}
		}
		return allPoints;
	}
	
	public static void main(String[] args) {
		test1();
		test2();
	}
	
	public static void test1() {
		float[] arr = new float[9];
		for (int i = 0; i < arr.length; i ++) {
			arr[i] = i;
		}
		 ArrayImg<FloatType, FloatArray> rai = ArrayImgs.floats(arr, new long[] {3, 3});
		 ScaleRangeTransformation preprocessing = new ScaleRangeTransformation();
		 Tensor<FloatType> tt = Tensor.build("name", "xy", rai);
		 preprocessing.applyInPlace(tt);
		 System.out.print(true);
	}
	
	public static void test2() {
		float[] arr = new float[18];
		for (int i = 0; i < arr.length; i ++) {
			arr[i] = i;
		}
		 ArrayImg<FloatType, FloatArray> rai = ArrayImgs.floats(arr, new long[] {3, 3, 2});
		 ScaleRangeTransformation preprocessing = new ScaleRangeTransformation();
		 preprocessing.setAxes("xy");
		 preprocessing.setMaxPercentile(99);
		 preprocessing.setMinPercentile(1);
		 Tensor<FloatType> tt = Tensor.build("name", "xyc", rai);
		 preprocessing.applyInPlace(tt);
		 System.out.print(true);
	}
}
