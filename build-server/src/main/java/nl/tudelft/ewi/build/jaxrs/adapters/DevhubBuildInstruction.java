package nl.tudelft.ewi.build.jaxrs.adapters;

public class DevhubBuildInstruction extends AdaptedMavenBuildInstruction {

	public DevhubBuildInstruction() {
		setPhases(new String[] {"test" } );
		setWithDisplay(true);
	}
	
	@Override
	public String getImage() {
		return "devhub";
	}

}
