package nl.tudelft.ewi.build.jaxrs.adapters;

import java.util.List;

public abstract class AdaptedBuildInstruction {
	
	public abstract String getImage();
	
	public abstract List<String> getCommand();

}
