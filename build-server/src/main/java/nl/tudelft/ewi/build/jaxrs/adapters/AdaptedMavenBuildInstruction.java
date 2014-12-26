package nl.tudelft.ewi.build.jaxrs.adapters;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
@JsonIgnoreProperties(ignoreUnknown=true)
public class AdaptedMavenBuildInstruction extends AdaptedBuildInstruction {
	
	private boolean withDisplay;
	
	private String[] phases;

	@Override
	public String getImage() {
		return "java-maven";
	}

	@Override
	public List<String> getCommand() {
		List<String> partials = Lists.newArrayList();
		if (withDisplay) {
			partials.add("with-xvfb");
		}
		partials.add("mvn -B");
		for (String phase : phases) {
			partials.add(phase);
		}
		
		return partials;
	}
	
}
