
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

import eu.unicore.security.wsutil.client.authn.DelegationSpecification

import de.fzj.unicore.wsrflite.xmlbeans.client.BaseWSRFClient;


import javax.xml.namespace.QName;

import javax.xml.xpath.*;

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
	int queued = 0;
	int running = 0;
	int stagingin = 0;
	int stagingout = 0;

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
			GetActivityStatusesDocument doc = GetActivityStatusesDocument.Factory.newInstance();
			GetActivityStatusesType type = doc.addNewGetActivityStatuses();
			
			type.setActivityIdentifierArray(query);

			GetActivityStatusesResponseDocument response = factory.getActivityStatuses(doc);

			for(GetActivityStatusResponseType r : response.getGetActivityStatusesResponse().getResponseArray())
			{
				ActivityStatusType status = r.getActivityStatus();
				ActivityStateEnumeration.Enum state = status.getState();
				EndpointReferenceType epr = r.getActivityIdentifier();
				String id = epr.getAddress().getStringValue();
				String jobName = jobNameMap.get(id);
				//			System.out.println("job name: "+jobName);


				if(ActivityStateEnumeration.RUNNING.equals(state))
				{
					// check the sub-state
					if(WSUtilities.extractAllMatchingElements(status, new QName("http://schemas.ogf.org/hpcp/2007/01/fs","Queued")).length > 0)
					{
						queued++;
					}
					else if(WSUtilities.extractAllMatchingElements(status, new QName("http://schemas.ogf.org/hpcp/2007/01/fs","Staging-In")).length > 0)
					{
						stagingin++;
					}
					else if(WSUtilities.extractAllMatchingElements(status, new QName("http://schemas.ogf.org/hpcp/2007/01/fs","Staging-Out")).length > 0)
					{
						stagingout++;
					}
					else running++;
				}
				else if(ActivityStateEnumeration.FAILED.equals(state) 
						|| ActivityStateEnumeration.FINISHED.equals(state)
						|| ActivityStateEnumeration.CANCELLED.equals(state))
				{
					jobNameMap.remove(id);
					System.out.println("Status for job "+jobName+" is '"+state+"', "+jobNameMap.size()+" jobs remaining.");
					
					
					//def xpath = XPathFactory.newInstance().newXPath()
					// fix to avoid xpath error in old saxon libs
					// stackoverflow.com/questions/7914915/syntax-error-in-javax-xml-xpath-xpathfactory-provider-configuration-file-of-saxon
					def xpf = XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI,"net.sf.saxon.xpath.XPathFactoryImpl",ClassLoader.getSystemClassLoader())
					def xpath = xpf.newXPath()
					def enode = xpath.evaluate( "/*[local-name() = 'ActivityStatus']/*[local-name() = 'ExitCode']", status.newDomNode(), XPathConstants.NODE )
					
					
					if(jobName.contains("MUST-FAIL"))
					{
						if(!ActivityStateEnumeration.FAILED.equals(state))
						{
							exitCode++;
						}
					}
					
					// not interoperable with Genesis
					
					else if(ActivityStateEnumeration.FINISHED.equals(state) && enode!=null) {
							if( status.getExitCode() > 0 ){
							 exitCode++;
							 }
					}
							
					
					else if(ActivityStateEnumeration.FAILED.equals(state))
					{
						if(status.getFault() != null)
						{
							System.out.println("This is the error message: "+status.getFault().getFaultstring());
						}
						exitCode++;
					}
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
				}
			}
		}
	}
	if(queued == 1) System.out.println(queued+" job is still queued...");
	if(queued > 1) System.out.println(queued+" jobs are still queued...");
	if(running == 1) System.out.println(running+" job is still running...");
	if(running > 1) System.out.println(running+" jobs are still running...");
	if(stagingin == 1) System.out.println(stagingin+" job is still staging in...");
	if(stagingin > 1) System.out.println(stagingin+" jobs are still staging in...");
	if(stagingout == 1) System.out.println(stagingout+" job is still staging out...");
	if(stagingout > 1) System.out.println(stagingout+" jobs are still staging out...");
	
	Thread.sleep(5000);

}
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
		securityProperties = configurationProvider.getClientConfiguration(factoryEpr,DelegationSpecification.STANDARD)
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


