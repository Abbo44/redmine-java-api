/*
   Copyright 2010-2011 Alexey Skorokhodov.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.alskor.redmine.internal;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alskor.redmine.RedmineManager;
import org.alskor.redmine.beans.Issue;
import org.alskor.redmine.beans.Project;
import org.alskor.redmine.beans.User;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.mapping.MappingException;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.xml.sax.InputSource;

public class RedmineXMLParser {

	private static final int UNKNOWN = -1;
	private static final String MAPPING_PROJECTS_LIST = "/mapping_projects_list.xml";
	private static final String MAPPING_ISSUES = "/mapping_issues_list.xml";
	private static final String MAPPING_USERS = "/mapping_users.xml";
	
	// TODO optimize : pre-load xml 
	private static final Map<Class, String> configFilesMap = new HashMap<Class, String>() {
		private static final long serialVersionUID = 1L;
		{
			put(User.class, MAPPING_USERS);
			put(Issue.class, MAPPING_ISSUES);
			put(Project.class, MAPPING_PROJECTS_LIST);
		}
	};

	public static Issue parseIssueFromXML(String xml) throws RuntimeException {
		return parseObjectFromXML(Issue.class, xml);
	}

	public static Project parseProjectFromXML(String xml)
			throws RuntimeException {
		return parseObjectFromXML(Project.class, xml);
	}

	public static List<Issue> parseIssuesFromXML(String xml)
			throws RuntimeException {
		xml = removeBadTags(xml);
		return parseObjectsFromXML(Issue.class, xml);
	}

	// see bug https://www.hostedredmine.com/issues/8240
	private static String removeBadTags(String xml) {
		return xml.replaceAll("<estimated_hours></estimated_hours>", "");
	}

	/**
	 * XML contains this line near the top: <issues type="array" limit="25"
	 * total_count="103" offset="0"> need to parse "total_count" value
	 * 
	 * @return -1 (UNKNOWN) if can't parse - which means that the string is
	 *         invalid / generated by an old Redmine version
	 */
	public static int parseIssuesTotalCount(String issuesXML) {
		String reg = "<issues type=\"array\" limit=.+ total_count=\""; // \\d+
																		// \" offset=\".+";
		// System.out.println(issuesXML);
		// System.out.println(reg);
		Pattern pattern = Pattern.compile(reg);
		Matcher matcher = pattern.matcher(issuesXML);
		int result = UNKNOWN;
		if (matcher.find()) {

			int indexBeginNumber = matcher.end();

			String tmp1 = issuesXML.substring(indexBeginNumber);
			int end = tmp1.indexOf('"');
			String numStr = tmp1.substring(0, end);
			result = Integer.parseInt(numStr);
		}
		return result;

	}

	public static List<Project> parseProjectsFromXML(String xml) {
		return parseObjectsFromXML(Project.class, xml);
	}

	private static Unmarshaller getUnmarshaller(String configFile,
			Class<?> classToUse) {
//		String configFile = configFilesMap.get(classToUse);
		InputSource inputSource = new InputSource(
				RedmineXMLParser.class.getResourceAsStream(configFile));
		ClassLoader cl = RedmineXMLParser.class.getClassLoader();
		// Note: Castor XML is packed in a separate OSGI bundle, so
		// must set the classloader so that Castor will see our classes
		Mapping mapping = new Mapping(cl);
		mapping.loadMapping(inputSource);

		Unmarshaller unmarshaller;
		try {
			unmarshaller = new Unmarshaller(mapping);
		} catch (MappingException e) {
			throw new RuntimeException(e);
		}
		unmarshaller.setClass(classToUse);
		return unmarshaller;
	}

	/**
	 * @throws  RuntimeException if the text does not start with a valid XML tag.
	 */
	private static boolean verifyStartsAsXML(String text) {
		String XML_START_PATTERN = "<?xml version="; // "1.0"
														// encoding="UTF-8"?>";
		String lines[] = text.split("\\r?\\n");
		if ((lines.length > 0) && lines[0].startsWith(XML_START_PATTERN)) {
			return true;
		} else {
			// show not more than 500 chars
			int charsToShow = text.length() < 500 ? text.length() : 500;
			throw new RuntimeException(
					"RedmineXMLParser: can't parse the response. This is not a valid XML:\n\n"
							+ text.substring(0, charsToShow) + "...");
		}

	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> parseObjectsFromXML(Class<T> classs, String body) {
		System.out.println("parseObjectsFromXML:" + body);
		verifyStartsAsXML(body);
		String configFile = configFilesMap.get(classs);
		Unmarshaller unmarshaller = getUnmarshaller(configFile, ArrayList.class);

		List<T> list = null;
		StringReader reader = null;
		try {
			reader = new StringReader(body);
			list = (ArrayList<T>) unmarshaller.unmarshal(reader);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return list;
	}

	public static <T> T parseObjectFromXML(Class<T> classs, String xml) {
		verifyStartsAsXML(xml);
		String configFile = configFilesMap.get(classs);
		Unmarshaller unmarshaller = getUnmarshaller(configFile, classs);

		T obj = null;
		StringReader reader = null;
		try {
//			 System.err.println(xml);
			reader = new StringReader(xml);
			obj = (T) unmarshaller.unmarshal(reader);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return obj;
	}

	public static List<User> parseUsersFromXML(String body) {
		return parseObjectsFromXML(User.class, body);
	}

	public static User parseUserFromXML(String body) {
		return parseObjectFromXML(User.class, body);
	}
	
	public static String convertObjectToXML(Object obj) {
		String configfile = configFilesMap.get(obj.getClass());
		StringWriter writer = new StringWriter();
		try {
			Marshaller m = getMarshaller(configfile, writer);
			m.marshal(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return writer.toString();
	}

	private static Marshaller getMarshaller(String configFile, Writer writer) {
		InputSource inputSource = new InputSource(
				RedmineManager.class.getResourceAsStream(configFile));
		Mapping mapping = new Mapping();
		mapping.loadMapping(inputSource);

		Marshaller marshaller;
		try {
			marshaller = new Marshaller(writer);
			marshaller.setMapping(mapping);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return marshaller;
	}

	/**
	 * sample parameter:
	 * <pre>
	 * 	&lt;?xml version="1.0" encoding="UTF-8"?>
	 *	&lt;errors>
  	 *		&lt;error>Name can't be blank&lt;/error>
  	 *		&lt;error>Identifier has already been taken&lt;/error>
	 *	&lt;/errors>
	 * </pre>
	 * @param responseBody
	 * @return
	 */
	public static List<String> parseErrors(String responseBody) {
		List<String> errors = new ArrayList<String>();
		/* I don't want to use Castor XML here with all these "include mapping" for errors file 
		* and making sure the mapping files are accessible in a plugin/jar/classpath and so on */
		String lines[] = responseBody.split("\\r?\\n");
		// skip first two lines: xml declaration and <errors> tag
		int lineToStartWith = 2;
		// skip last line with </errors> tag
		int lastLine = lines.length-1;
		String openTag = "<error>";
		String closeTag = "</error>";
		for (int i=lineToStartWith; i<lastLine;i++ ) {
			int begin = lines[i].indexOf(openTag) + openTag.length();
			int end = lines[i].indexOf(closeTag);
			errors.add(lines[i].substring(begin, end));
		}
//		errors.add(responseBody);
		return errors;
	}
}
