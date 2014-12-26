package nl.tudelft.ewi.build.jaxrs.adapters;

import lombok.Data;

@Data
public class AdaptedBuildRequest {

	private AdaptedBuildInstruction instruction;
	
	private AdaptedDirectoryPreparer source;
	
	private String callbackUrl;

	private Long timeout;
	
}
