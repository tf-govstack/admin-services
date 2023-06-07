package io.mosip.hotlist.util;



import io.mosip.hotlist.constant.RegistrationConstants;

public enum EventEnum {
	
	TOKEN_GENERATION_FAILED("RES-SER-409",RegistrationConstants.SYSTEM,"Generating token","Token generation failed","RES-SER","Residence service","NO_ID","NO_ID_TYPE",RegistrationConstants.APPLICATIONID,RegistrationConstants.APPLICATIONNAME),
	TEMPLATE_EXCEPTION("RES-SER-415",RegistrationConstants.SYSTEM,"Get template","Template Exception","RES-SER","Residence service","NO_ID","NO_ID_TYPE",RegistrationConstants.APPLICATIONID,RegistrationConstants.APPLICATIONNAME);
	
	private final String eventId;

	private final String type;
	
	private String name;

	private String description;
	
	private String moduleId;
	
	private String moduleName;
	
	private String id;
	
	private String idType;
	
	private String applicationId;
	
	private String applicationName;

	private EventEnum(String eventId, String type, String name, String description,String moduleId,String moduleName,String id,String idType,String applicationId,String applicationName) {
		this.eventId = eventId;
		this.type = type;
		this.name = name;
		this.description = description;
		this.moduleId=moduleId;
		this.moduleName=moduleName;
		this.id=id;
		this.idType=idType;
		this.applicationId=applicationId;
		this.applicationName=applicationName;
		
	}

	public String getEventId() {
		return eventId;
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getModuleName() {
		return moduleName;
	}

	public String getId() {
		return id;
	}

	public String getIdType() {
		return idType;
	}
	
	public void setDescription(String des)
	{
		this.description=des;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setId(String id) {
		this.id=id;
	}
	
	public void setName(String name) {
		this.name=name;
	}

	public String getApplicationName() {
		return applicationName;
	}

	/*
	 * Replace %s value in description and id with second parameter passed
	 */

	public static EventEnum getEventEnumWithValue(EventEnum e,String s)
	{
		e.setDescription(String.format(e.getDescription(),s));
		if(e.getId().equalsIgnoreCase("%s"))
			e.setId(s);
		return e;
	}
	
   /*
    * Replace %s value in description and id with second parameter passed
    *  and name property of enum  with third parameter
    */
	public static EventEnum getEventEnumWithValue(EventEnum e,String edescription,String ename)
	{
		e.setDescription(String.format(e.getDescription(),edescription));
		if(e.getId().equalsIgnoreCase("%s"))
			e.setId(edescription);
		e.setName(String.format(e.getName(),ename));
		return e;
	}
	
	/*
	 * Replace second parameter with %s in name property and in description property
	 */

	public static EventEnum getEventEnumWithDynamicName(EventEnum e,String s)
	{
		e.setName(Character.toUpperCase(s.charAt(0))+s.substring(1));
		e.setDescription(String.format(e.getDescription(),s));
		return e;
	}
	
}
