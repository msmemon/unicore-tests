
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ggf.schemas.bes.x2006.x08.besFactory.ActivityStateEnumeration;
import org.ggf.schemas.bes.x2006.x08.besFactory.GetActivityStatusResponseType;
import org.ggf.schemas.bes.x2006.x08.besFactory.GetActivityStatusesDocument;
import org.ggf.schemas.bes.x2006.x08.besFactory.GetActivityStatusesResponseDocument;
import org.ggf.schemas.bes.x2006.x08.besFactory.GetActivityStatusesType;
import org.ggf.schemas.bes.x2006.x08.besFactory.ActivityStatusType;
import org.w3.x2005.x08.addressing.EndpointReferenceType;

import de.fzj.unicore.wsrflite.xmlbeans.WSUtilities;

import de.fzj.unicore.bes.client.FactoryClient;
import de.fzj.unicore.ucc.util.Builder;

import de.fzj.unicore.wsrflite.xmlbeans.client.BaseWSRFClient;
import eu.unicore.security.wsutil.client.authn.DelegationSpecification


import javax.xml.namespace.QName;

String[] args = commandLine.args;


Map<String,FactoryClient> factories = new HashMap<String, FactoryClient>();
Map<String,List<EndpointReferenceType>> entireJobMap = new HashMap<String, List<EndpointReferenceType>>();
Map<String,String> jobNameMap = new HashMap<String, String>();
int numJobs = args.length;
int chunkSize = 1;
if(numJobs > 10) chunkSize = 10;
if(numJobs > 100) chunkSize = 100;
if(numJobs > 1000) chunkSize = 1000;


for(int i = 1; i < numJobs; i++)
{
	String arg = args[i];
	File f = new File(arg);
	if(f.exists())
	{
		String jobName = getActivityName(f);
		EndpointReferenceType epr = getJobEpr(f);
		securityProperties = configurationProvider.getClientConfiguration(epr,DelegationSpecification.STANDARD)
		BaseWSRFClient client = new BaseWSRFClient(epr,securityProperties);
		try
		{
			
			client.destroy();
		} catch (Exception e) {
			System.out.println("Unable to clean up job "+jobName+": "+e.getMessage());
		}
		f.delete();
		String propsFileName = chompExtension(f);
		propsFileName += "properties";
		File propsFile = new File(propsFileName);
		propsFile.delete();
	}
	if(i == numJobs-1)
	{
		System.out.println("... done");
	}
	else if(i%chunkSize==0)
	{
		if(i == 1) System.out.println("Cleaned up "+i+" job, "+(numJobs-i-1)+" remaining.");
		else System.out.println("Cleaned up "+i+" jobs, "+(numJobs-i-1)+" remaining.");
	}
}


protected static String getBaseName(File f)
{
	String fName = f.getName();
	return fName.substring(0,fName.lastIndexOf(".")+1);
}

protected static String chompExtension(File f)
{
	String fName = f.getAbsolutePath();
	return fName.substring(0,fName.lastIndexOf(".")+1);
}

protected String getActivityName(File f) {
	Pattern jobNamePattern = Pattern.compile(".*JobName\\s*>([^<]+)<.*");

	String propsFileName = chompExtension(f);

	propsFileName += "properties";
	File propsFile = new File(propsFileName);
	try {


		FileReader r = new FileReader(propsFile);
		BufferedReader buffered = new BufferedReader(r);
		String s = buffered.readLine();
		while(s != null)
		{
			Matcher m = jobNamePattern.matcher(s);
			if(m.matches())
			{
				return m.group(1);
			}
			s = buffered.readLine();
		}
	} catch (Exception e) {
		return null;
	}
	return null;

}



protected EndpointReferenceType getJobEpr(File f) {
	Builder builder = new Builder(f);
	String eprString = builder.getProperty("activity-epr");
	if (eprString == null) {
		System.err.println("Job EPR not found! Maybe <"+f+"> has not been produced by UCC?");
	}

	try {
		return EndpointReferenceType.Factory.parse(eprString);

	} 
	catch (Exception xe){
		System.err.println("Error parsing job EPR: "+xe.getMessage());
	}
	return null;
}


