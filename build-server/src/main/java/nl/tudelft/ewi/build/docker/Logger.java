package nl.tudelft.ewi.build.docker;

public interface Logger {
	
	void initialize(Identifiable container);
	
	void onStart();

	void onNextLine(String line);

	void onClose(int statusCode);
	
}