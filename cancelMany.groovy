
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.ggf.schemas.bes.x2006.x08.besFactory.TerminateActivitiesResponseDocument;
import org.ggf.schemas.bes.x2006.x08.besFactory.TerminateActivitiesDocument;
import org.ggf.schemas.bes.x2006.x08.besFactory.TerminateActivitiesType;
import org.ggf.schemas.bes.x2006.x08.besFactory.TerminateActivitiesResponseType;
import org.ggf.schemas.bes.x2006.x08.besFactory.TerminateActivityResponseType;

import org.w3.x2005.x08.addressing.EndpointReferenceType;

import de.fzj.unicore.wsrflite.xmlbeans.WSUtilities;

import de.fzj.unicore.bes.client.FactoryClient;
import de.fzj.unicore.ucc.util.Builder;

import de.fzj.unicore.wsrflite.xmlbeans.client.BaseWSRFClient;
import eu.unicore.security.wsutil.client.authn.DelegationSpecification;

import javax.xml.namespace.QName;

String[] args = commandLine.args;
int chunkSize = 50;

Map<String,FactoryClient> factories = new HashMap<String, FactoryClient>();
Map<String,List<EndpointReferenceType>> entireJobMap = new HashMap<String, List<EndpointReferenceType>>();
Map<String,String> jobNameMap = new HashMap<String, String>();
for(int i = 1; i < args.length; i++)
{
	String arg = args[i];
	File f = new File(arg);
	if(f.exists())
	{
		FactoryClient factory = buildFactory(f);

		if(factory == null) continue;
		String factoryUrl = factory.getUrl();
		if(!factories.containsKey(factoryUrl)) factories.put(factoryUrl,factory);
		List<EndpointReferenceType> eprs = entireJobMap.get(factoryUrl);
		if(eprs == null)
		{
			eprs = new ArrayList<EndpointReferenceType>();
			entireJobMap.put(factoryUrl, eprs);
		}
		EndpointReferenceType epr = getJobEpr(f);
		if(epr != null) 
		{
			eprs.add(epr);
			String jobName = getActivityName(f);
			if(jobName == null)
			{
				jobName = getBaseName(f);
			}
			jobNameMap.put(epr.getAddress().getStringValue(), jobName);
		}
	}
}


int exitCode = 0;

while(!entireJobMap.isEmpty())
{
	for(String factoryUrl : factories.keySet())
	{

		//		System.out.println("factory-url: "+factoryUrl);
		FactoryClient factory = factories.get(factoryUrl);
		List<EndpointReferenceType> eprs = entireJobMap.get(factoryUrl);
		if(eprs == null || eprs.size() == 0) continue;
		EndpointReferenceType[] eprArray = eprs.toArray(new EndpointReferenceType[eprs.size()]);
		for(int i = 0; i < eprs.size(); i+= chunkSize)
		{
			int max = Math.min(i+(chunkSize-1),eprs.size()-1);
			int arrLength = max-i+1;
			EndpointReferenceType[] query = new EndpointReferenceType[arrLength];
			System.arraycopy(eprArray,i,query,0,arrLength);
			TerminateActivitiesDocument doc = TerminateActivitiesDocument.Factory.newInstance();
			TerminateActivitiesType type = doc.addNewTerminateActivities();

			type.setActivityIdentifierArray(query);

			TerminateActivitiesResponseDocument response = factory.terminateActivities(doc);

			for(TerminateActivityResponseType r : response.getTerminateActivitiesResponse().getResponseArray())
			{
				boolean terminated = r.getTerminated();

				EndpointReferenceType epr = r.getActivityIdentifier();
				String id = epr.getAddress().getStringValue();
				String jobName = jobNameMap.get(id);

				jobNameMap.remove(id);
				for(Iterator<EndpointReferenceType> it = eprs.iterator();it.hasNext();)
				{
					EndpointReferenceType current = it.next();
					if(current.getAddress().getStringValue().equalsIgnoreCase(epr.getAddress().getStringValue()))
					{
						it.remove();
					}
				}

				if(eprs.size() == 0)
				{
					entireJobMap.remove(factoryUrl);
				}

				if(!terminated)
				{
					System.err.println("Job "+jobName+" could not be cancelled, the following fault was reported: "+r.getFault())
					exitCode++;
				}

			}
		}
	}
}
System.err.println("Exit code: "+exitCode);
System.exit(exitCode);


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

protected FactoryClient buildFactory(File f) {
	Builder builder = new Builder(f);
	String factoryStr = builder.getProperty("factory-epr");
	try {
		EndpointReferenceType factoryEpr = EndpointReferenceType.Factory
		.parse(factoryStr);
		securityProperties = configurationProvider.getClientConfiguration(it,DelegationSpecification.STANDARD)
		FactoryClient factory = new FactoryClient(factoryEpr.getAddress()
				.getStringValue(), factoryEpr, securityProperties);
		return factory;
	} catch (Exception e) {
		System.err.println("Couldn't get factory epr: "+e.getMessage());
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


