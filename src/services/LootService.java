/*******************************************************************************
 * Copyright (c) 2013 <Project SWG>
 * 
 * This File is part of NGECore2.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Using NGEngine to work with NGECore2 is making a combined work based on NGEngine. 
 * Therefore all terms and conditions of the GNU Lesser General Public License cover the combination.
 ******************************************************************************/
package services;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import resources.objects.creature.CreatureObject;
import resources.objects.group.GroupObject;
import resources.objects.loot.LootGroup;
import resources.objects.loot.LootRollSession;
import resources.objects.tangible.TangibleObject;
import resources.objects.weapon.WeaponObject;
import main.NGECore;
import engine.resources.objects.SWGObject;
import engine.resources.scene.Planet;
import engine.resources.service.INetworkDispatch;
import engine.resources.service.INetworkRemoteEvent;

/** 
 * @author Charon 
 */

public class LootService implements INetworkDispatch {
	
	private NGECore core;

	public LootService(NGECore core) {
		this.core = core;
	}

	@Override
	public void insertOpcodes(Map<Integer, INetworkRemoteEvent> swgOpcodes, Map<Integer, INetworkRemoteEvent> objControllerOpcodes) {
		
	}

	@Override
	public void shutdown() {
		
	}
	
	public void handleLootRequest(CreatureObject requester, TangibleObject lootedObject) {
		
		GroupObject group = (GroupObject) core.objectService.getObject(requester.getGroupId());
		
		if (lootedObject.isLooted() || lootedObject.isLootLock() || (group == null && !lootedObject.getKiller().equals(requester)) || (group != null && !group.getMemberList().contains(lootedObject.getKiller())))
			return;
		
		lootedObject.setLootLock(true);
		
		if (requester.getCustomName().contains("Kun")){
			requester.setCashCredits(requester.getCashCredits()+1);
			requester.sendSystemMessage("You looted 1 credit.", (byte)1); 
			lootedObject.setLooted(true);
			return;
		}
				
		LootRollSession lootRollSession = new LootRollSession(requester,lootedObject);
		
		handleCreditDrop(requester,lootedObject);
		
		lootSituationAssessment(requester,lootedObject,lootRollSession);
				
		CreatureObject lootedCreature = (CreatureObject) lootedObject;
		
		//TreeSet<TreeMap<String,Integer>> lootSpec = lootedObject.getLootSpecification();
		 List<LootGroup> lootGroups = lootedCreature.getLootGroups();
		 System.out.println("lootGroups size " + lootGroups.size());
		 Iterator<LootGroup> iterator = lootGroups.iterator();
	     	    
	    while (iterator.hasNext()){
	    	LootGroup lootGroup = iterator.next();
	    	int groupChance = lootGroup.getLootGroupChance();
	    	int lootGroupRoll = new Random().nextInt(100);
	    	if (lootGroupRoll <= groupChance){    	
	    		System.out.println("this lootGroup will drop something");
	    		handleLootGroup(lootGroup,lootRollSession); //this lootGroup will drop something e.g. {kraytpearl_range,krayt_tissue_rare}	    		
	    	}		
	    }
	    
	    // Rare Loot System Stage (Is in place for all looted creatures)
	    if (lootRollSession.isAllowRareLoot()){
	    	int randomRareLoot = new Random().nextInt(100);
	    	int chanceRequirement = 1; 
	    	if (lootRollSession.isIncreasedRLSChance())
	    		chanceRequirement+=3; // RLS chance is at 4% for groupsize >= 4
	    	if (randomRareLoot <= chanceRequirement){ 
	    		handleRareLootChest(lootRollSession);
	    	}
	    }
		
	    
	    // ********** Phase 1 complete, loot items determined **********
	    // stored in the lootSession
	    
	    // Distribute the loot drops according to group loot rules	    
	    // For now just spawn items into requester's inventory
	    
	    if (lootRollSession.getErrorMessages().size()>0){
	    	for (String msg : lootRollSession.getErrorMessages()){
	    		// ToDo: Show this for each group member later!
	    		requester.sendSystemMessage(msg,(byte) 1);
	    		lootedObject.setLootLock(false);
	    		return;
	    	}
	    }
	    
    	SWGObject requesterInventory = requester.getSlottedObject("inventory");
    	
    	for (TangibleObject droppedItem : lootRollSession.getDroppedItems()){		    
    		
	    	requesterInventory.add(droppedItem);
	    	if (droppedItem.getAttachment("LootItemName").toString().contains("Loot Chest")){
	    		requester.playEffectObject("clienteffect/level_granted.cef", "");
	    	}
    	}
    	
    	lootedObject.setLooted(true);
    	 
       
	    // ToDo: Group loot settings etc.  actual loot chance was lootgroupchance*lootchance    
	}
	
	
	private void handleLootGroup(LootGroup lootGroup,LootRollSession lootRollSession){
		
		int[] lootPoolChances = lootGroup.getLootPoolChances();
		String[] lootPoolNames = lootGroup.getLootPoolNames();
		if (lootPoolChances==null || lootPoolNames==null){
			System.err.println("Lootpools are null!");
			return;
		}
		if (lootPoolChances.length==0 || lootPoolNames.length==0){
			System.err.println("No Lootpools in Lootgroup!");
			return;
		}
		
		int randomItemFromGroup = new Random().nextInt(100);
		int remainder = 0; // [10,20,30,34,5,1] 
		
		for(int i=0;i<lootPoolChances.length;i++) {
			remainder += lootPoolChances[i]; 
	    	if (randomItemFromGroup <= remainder){ 		
	    		System.out.println("this loot pool will drop something"); // e.g. kraytpearl_range
	    		handleLootPool(lootPoolNames[i],lootRollSession); // This loot pool will drop something	
	    		break;
	    	}			 
		}
	}
		
	private void handleLootPool(String poolName,LootRollSession lootRollSession){

		// Fetch the loot pool data from the poolName.py script		
		String path = "scripts/loot/lootPools/"+poolName.toLowerCase(); 
		Vector<String> itemNames = (Vector<String>)core.scriptService.fetchStringVector(path,"itemNames");
		
		for (String s : itemNames){
			System.out.println("template: " + s);
		}
		
		Vector<Integer> itemChances = (Vector<Integer>)core.scriptService.fetchIntegerVector(path,"itemChances");
				
		int randomItemFromPool = new Random().nextInt(100);
		int remainder = 0; // [10,20,30,34,5,1]
		
		for (int i=0;i<itemNames.size();i++){
			remainder += itemChances.get(i); 
			if (randomItemFromPool<=remainder){
				// this element has been chosen e.g. kraytpearl_flawless
				handleLootPoolItems(itemNames.get(i), lootRollSession);
				break;
			}						
		}
	}	
	
	private static class DirectoriesFilter implements Filter<Path> {
	    @Override
	    public boolean accept(Path entry) throws IOException {
	        return Files.isDirectory(entry);
	    }
	}
	
	private void handleLootPoolItems(String itemName,LootRollSession lootRollSession){

		List<String> subfolders = new ArrayList<String>(); // Consider all sub-folders		
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(FileSystems.getDefault().getPath("scripts/loot/lootItems/"), new DirectoriesFilter())) {
		        for (Path p : ds) {
		        	subfolders.add(p.getFileName().toString());
		        }
		    } catch (IOException e) {
		    	lootRollSession.addErrorMessage("File system check caused an error. Please contact Charon about this issue.");
	        	return;
		    }

		String itemPath = "scripts/loot/lootItems/"+itemName.toLowerCase()+".py";
		File file = new File(itemPath);
		if (!file.isFile()){
			for (String subfolderName : subfolders){
				itemPath = "scripts/loot/lootItems/"+ subfolderName +"/"+itemName.toLowerCase()+".py"; 
				File subfile = new File(itemPath);
				if (subfile.isFile())
					break;			
			}
		}

		File checkfile = new File(itemPath);
		if (!checkfile.isFile()){
			String errorMessage = "Loot item  '" + itemName + "'  not found in file system. Please contact Charon about this issue.";
			lootRollSession.addErrorMessage(errorMessage);
			return;
		}
		
		itemPath = itemPath.substring(0, itemPath.length()-3); // remove the file type

		String customName = "";
		String itemTemplate = "";
		Vector<String> itemTemplates = null;
		int stackCount = 1;
		int biolink = 0;
		int requiredCL = 1;
		String requiredProfession = "";
		String requiredFaction = "";
		Vector<String> customizationAttributes = null;
		Vector<Integer> customizationValues = null;
		Vector<String> itemStats = null;
				
		if(core.scriptService.getMethod(itemPath,"","itemTemplate")==null){
			String errorMessage = "Loot item  '" + itemName + "'  has no template function assigned in its script. Please contact Charon about this issue.";
			lootRollSession.addErrorMessage(errorMessage);
			return;
		}
		
		//itemTemplate = (String)core.scriptService.fetchString(itemPath,"itemTemplate");
	
		itemTemplates = (Vector<String>)core.scriptService.fetchStringVector(itemPath,"itemTemplate"); 
		if (itemTemplates.size()==1)
			itemTemplate = itemTemplates.get(0);
		if (itemTemplates.size()>1){
			itemTemplate = itemTemplates.get(new Random().nextInt(itemTemplates.size()-1));
		}
			
		
		
		
		
		// only consider the following variables, if they are in the python-script file
		if(core.scriptService.getMethod(itemPath,"","customItemName")!=null) 
			customName = (String)core.scriptService.fetchString(itemPath,"customItemName");
		
		if(core.scriptService.getMethod(itemPath,"","customItemStackCount")!=null)
			stackCount = (Integer)core.scriptService.fetchInteger(itemPath,"customItemStackCount");
		
		if(core.scriptService.getMethod(itemPath,"","customizationAttributes")!=null)
			customizationAttributes = (Vector<String>)core.scriptService.fetchStringVector(itemPath,"customizationAttributes");
		
		if(core.scriptService.getMethod(itemPath,"","customizationValues")!=null)
			customizationValues = (Vector<Integer>)core.scriptService.fetchIntegerVector(itemPath,"customizationValues");
		
		if(core.scriptService.getMethod(itemPath,"","itemStats")!=null)
			itemStats = (Vector<String>)core.scriptService.fetchStringVector(itemPath,"itemStats");
		
		if(core.scriptService.getMethod(itemPath,"","biolink")!=null)
			biolink = (Integer)core.scriptService.fetchInteger(itemPath,"biolink");
		
		if(core.scriptService.getMethod(itemPath,"","requiredCL")!=null)
			requiredCL = (Integer)core.scriptService.fetchInteger(itemPath,"requiredCL");

		if(core.scriptService.getMethod(itemPath,"","requiredProfession")!=null)
			requiredProfession = (String)core.scriptService.fetchString(itemPath,"requiredProfession");
		
		if(core.scriptService.getMethod(itemPath,"","requiredFaction")!=null)
			requiredFaction = (String)core.scriptService.fetchString(itemPath,"requiredFaction");
		
				
		System.out.println("itemTemplate " + itemTemplate);
		
		TangibleObject droppedItem = createDroppedItem(itemTemplate,lootRollSession.getSessionPlanet());
    	
		droppedItem.setAttachment("LootItemName", itemName);
    	
		if (customName!=null)
			handleCustomDropName(droppedItem, customName);
    	
    	if (itemStats!=null){
    		if (itemStats.size()%3!=0){
    			String errorMessage = "Loot item  '" + itemName + "'  has a wrong number of itemstats. Please contact Charon about this issue.";
    			lootRollSession.addErrorMessage(errorMessage);
    			return;
    		}
    		handleStats(droppedItem, itemStats);
    	}
//    	if (customizationValues!=null)
//    		setCustomization(droppedItem, itemName);
    	
    	setCustomization(droppedItem, itemName); // for now
    	
    	handleSpecialItems(droppedItem, itemName);
		
    	if (requiredCL>1){
    		droppedItem.setIntAttribute("required_combat_level", requiredCL);
    	}
    	
    	if (requiredProfession.length()>0){
    		droppedItem.setStringAttribute("required_profession", requiredProfession);
    	}
    	
    	if (requiredFaction.length()>0){
    		droppedItem.setStringAttribute("required_faction", requiredFaction);
    	}
    	
		lootRollSession.addDroppedItem(droppedItem);
	}	
	
	private void handleCustomDropName(TangibleObject droppedItem,String customName) {
//		String customItemName = droppedItem.getCustomName();
//		if (customName.charAt(0) == '@' || customName.contains("_n:")) {
//				if (customName!=null) {
//			customName = ""; // Look the name up in some tre table
//			}
//		}
		droppedItem.setCustomName(customName);
	}
	
	private TangibleObject createDroppedItem(String template,Planet planet){
		TangibleObject droppedItem = (TangibleObject) core.objectService.createObject(template, planet);				
    	System.out.println("droppedItem " + droppedItem);
    	return droppedItem;
	}
	
	private void handleRareLootChest(LootRollSession lootRollSession){
		
		TangibleObject droppedItem = null;
		
		int legendaryRoll = new Random().nextInt(100);
		int exceptionalRoll = new Random().nextInt(100);
		int chancemodifier = 0;
		if (lootRollSession.isIncreasedRLSChance())
			chancemodifier += 15;
		
		if (legendaryRoll<2+chancemodifier){ 
			String itemTemplate="object/tangible/item/shared_rare_loot_chest_3.iff";
			droppedItem = createDroppedItem(itemTemplate,lootRollSession.getSessionPlanet());
			String itemName = "Legendary Loot Chest";
			droppedItem.setStfFilename("loot_n");
			droppedItem.setStfName("rare_loot_chest_3_n");
			droppedItem.setDetailFilename("loot_n");
			droppedItem.setDetailName("rare_loot_chest_3_d");
			droppedItem.setAttachment("LootItemName", itemName);
			droppedItem.getAttributes().put("@obj_attr_n:rare_loot_category", "\\#D1F56F Rare Item \\#FFFFFF ");
			fillLegendaryChest(droppedItem);
			
		} else if (exceptionalRoll<10+chancemodifier){
			String itemTemplate="object/tangible/item/shared_rare_loot_chest_2.iff";
			droppedItem = createDroppedItem(itemTemplate,lootRollSession.getSessionPlanet());
			String itemName = "Exceptional Loot Chest";
			droppedItem.setStfFilename("loot_n");
			droppedItem.setStfName("rare_loot_chest_2_n");
			droppedItem.setDetailFilename("loot_n");
			droppedItem.setDetailName("rare_loot_chest_2_d");
			droppedItem.setAttachment("LootItemName", itemName);
			droppedItem.getAttributes().put("@obj_attr_n:rare_loot_category", "\\#D1F56F Rare Item \\#FFFFFF ");
			fillExceptionalChest(droppedItem);
		} else {
			String itemTemplate="object/tangible/item/shared_rare_loot_chest_1.iff";
			droppedItem = createDroppedItem(itemTemplate,lootRollSession.getSessionPlanet());
			String itemName = "Rare Loot Chest";
			droppedItem.setStfFilename("loot_n");
			droppedItem.setStfName("rare_loot_chest_1_n");
			droppedItem.setDetailFilename("loot_n");
			droppedItem.setDetailName("rare_loot_chest_1_d");
			droppedItem.setAttachment("LootItemName", itemName);
			droppedItem.getAttributes().put("@obj_attr_n:rare_loot_category", "\\#D1F56F Rare Item \\#FFFFFF ");
			fillRareChest(droppedItem);
		}

		lootRollSession.addDroppedItem(droppedItem);
	}
	
	private void fillLegendaryChest(TangibleObject droppedItem){
		
	}
	
	private void fillExceptionalChest(TangibleObject droppedItem){
		
	}

	private void fillRareChest(TangibleObject droppedItem){
		
	}
		
	private void setCustomization(TangibleObject droppedItem,String itemName) {
		
		// Example color crystal
		if (itemName.contains("colorcrystal")) {
			System.out.println("colorcrystal");
			droppedItem.setCustomizationVariable("/private/index_color_1", (byte) new Random().nextInt(11));
		}
		
		// Example power crystal
		if (itemName.contains("powercrystal")) {
			System.out.println("powercrystal");
			droppedItem.setCustomizationVariable("/private/index_color_1", (byte) 0x21);  //  0x1F
		}
		
		// More general 
//		String path = "scripts/loot/lootItems/"+droppedItem.getCustomName().toLowerCase(); 
//		Vector<String> customizationPaths = (Vector<String>)core.scriptService.fetchStringVector(path,"itemCustomizationPaths");
//		Vector<Integer> customizationValues = (Vector<Integer>)core.scriptService.fetchIntegerVector(path,"itemCustomizationValues");
//		for (int i=0;i<customizationPaths.size();i++){
//			String attributePath = customizationPaths.get(i);
//			int attributeValue = customizationValues.get(i);
//			droppedItem.setCustomizationVariable(attributePath, (byte)attributeValue);
//		}
	}
	
	private void handleSpecialItems(TangibleObject droppedItem,String itemName) {
		if (itemName.contains("kraytpearl")){
			handleKraytPearl(droppedItem);
		}
		if (itemName.contains("powercrystal")){
			handlePowerCrystal(droppedItem);
		}	
	}
	
	private void handleStats(TangibleObject droppedItem, Vector<String> itemStats) {
		
		if (droppedItem.getTemplate().contains("/weapon")){
			WeaponObject weaponObject = (WeaponObject) droppedItem;
			for (int i=0;i<itemStats.size()/3;i++){
				String statName = itemStats.get(3*i);
				String minValue = itemStats.get(3*i+1);
				String maxValue = itemStats.get(3*i+2);
				setWeaponStat(weaponObject, statName, minValue, maxValue);
			}
		}
		
		if (droppedItem.getTemplate().contains("/armor")){
			for (int i=0;i<itemStats.size()/3;i++){
				String statName = itemStats.get(3*i);
				String minValue = itemStats.get(3*i+1);
				String maxValue = itemStats.get(3*i+2);
				setArmorStat(droppedItem, statName, minValue, maxValue);
			}
		}			
	}	
	
	private void handleCreditDrop(CreatureObject requester,TangibleObject lootedObject){
		int lootedCredits = 0;
		// Credit drop is depending on the CL of the looted CreatureObject
		// or if explicitely assigned in the .py script
		if (lootedObject instanceof CreatureObject){
			CreatureObject lootedCreature = (CreatureObject) lootedObject;
			int creatureCL = lootedCreature.getLevel();
			if (creatureCL<=0)
				creatureCL=1;
			//creatureCL = 90;
			int maximalCredits = (int)Math.floor(4*creatureCL + creatureCL*creatureCL*4/100); 
			int minimalCredits = (int)Math.floor(creatureCL*2 + maximalCredits/2); 
			int spanOfCredits  = maximalCredits - minimalCredits;
			if (spanOfCredits<=0)
				spanOfCredits=1;
			lootedCredits = minimalCredits + new Random().nextInt(spanOfCredits);
			requester.setCashCredits(requester.getCashCredits()+lootedCredits);
			requester.sendSystemMessage("You looted " + lootedCredits + " credits.", (byte)1); 
		}
		
		if (lootedObject instanceof TangibleObject){
			// This is for chests etc.
			// Check the py script
		}	
	}
	
	private void lootSituationAssessment(CreatureObject requester,TangibleObject lootedObject, LootRollSession lootRollSession){
		
		// reserved for possible necessities
	}
	
	// ************* Special items ************
	private void handleKraytPearl(TangibleObject droppedItem) {
		
		String itemName = (String)droppedItem.getAttachment("LootItemName");
		String qualityString = "";
		switch (itemName) {
		case "kraytpearl_cracked": 
			droppedItem.setStfFilename("static_item_n");
			droppedItem.setStfName("item_junk_imitation_pearl_01_02");
			droppedItem.setDetailFilename("static_item_d");
			droppedItem.setDetailName("item_junk_imitation_pearl_01_02");
			return;
		case "kraytpearl_scratched": 
			droppedItem.setStfFilename("static_item_n");
			droppedItem.setStfName("item_junk_imitation_pearl_01_01");
			droppedItem.setDetailFilename("static_item_d");
			droppedItem.setDetailName("item_junk_imitation_pearl_01_01");
			return;
		case "kraytpearl_poor": 
			qualityString="Poor";
			break;
		case "kraytpearl_fair": 
			qualityString="Fair";
			break;
		case "kraytpearl_good": 
			qualityString="Good";
			break;
		case "kraytpearl_quality": 
			qualityString="Quality";
			break;
		case "kraytpearl_select": 
			qualityString="Select";
			break;
		case "kraytpearl_premium": 
			qualityString="Premium";
			break;
		case "kraytpearl_flawless": 
			qualityString="Flawless";
			break;
		default:
			qualityString="Undetermined";
			break;
	}                               
		droppedItem.getAttributes().put("@obj_attr_n:condition", "100/100");
		droppedItem.getAttributes().put("@obj_attr_n:crystal_owner", "\\#D1F56F UNTUNED \\#FFFFFF ");		
		droppedItem.getAttributes().put("@obj_attr_n:crystal_quality", qualityString);	
		droppedItem.setAttachment("radial_filename", "tunable");
	}
	
	private void handlePowerCrystal(TangibleObject droppedItem) {
		
		String itemName = (String)droppedItem.getAttachment("LootItemName");
		String qualityString = "";
		switch (itemName) {
		
		case "powercrystal_poor": 
			qualityString="Poor";
			break;
		case "powercrystal_fair": 
			qualityString="Fair";
			break;
		case "powercrystal_good": 
			qualityString="Good";
			break;
		case "powercrystal_quality": 
			qualityString="Quality";
			break;
		case "powercrystal_select": 
			qualityString="Select";
			break;
		case "powercrystal_premium": 
			qualityString="Premium";
			break;
		case "powercrystal_flawless": 
			qualityString="Flawless";
			break;
		case "powercrystal_perfect": 
			qualityString="Perfect";
			break;
		default:
			qualityString="Undetermined";
			break;
	}                               
		droppedItem.getAttributes().put("@obj_attr_n:condition", "100/100");
		droppedItem.getAttributes().put("@obj_attr_n:crystal_owner", "\\#D1F56F UNTUNED \\#FFFFFF ");
		droppedItem.getAttributes().put("@obj_attr_n:crystal_quality", qualityString);	
		droppedItem.setAttachment("radial_filename", "tunable");
	}	
	
	private void setWeaponStat(WeaponObject weapon, String statName, String minValue, String maxValue){
		
		// weapon.setConditionDamage(100); shows 1000/926 ??!!
		
		if (statName.equals("attackspeed")){
			float value = (float) Float.parseFloat(minValue);
			weapon.setAttackSpeed(value);
		}
		
		if (statName.equals("mindamage")){		
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			weapon.setMinDamage(randomValue);
		}
		
		if (statName.equals("maxdamage")){
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			weapon.setMaxDamage(randomValue);
		}
	
		if (statName.equals("maxrange")){
			float value = (float) Float.parseFloat(maxValue);
			weapon.setMaxRange(value);
		}
		
		if (statName.equals("elemdamage")){
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			weapon.setElementalDamage(randomValue);
		}
		
		if (statName.equals("elemtype")){
			String result = "";
			if (minValue.length()==0)
				if (maxValue.length()==0)
					result = "Error";
			
			if (minValue.length()>0)
				result = minValue;
			if (maxValue.length()>0)
				result = maxValue;
			
			weapon.setElementalType(result);
		}
		
		if (statName.equals("damagetype")){
			String result = "";
			if (minValue.length()==0)
				if (maxValue.length()==0)
					result = "Error";
			
			if (minValue.length()>0)
				result = minValue;
			if (maxValue.length()>0)
				result = maxValue;
			
			weapon.setDamageType(result);
		}
		
		if (statName.equals("weapontype")){
			weapon.setWeaponType((int) Integer.parseInt(minValue));
		}

	}
	
	private void setArmorStat(SWGObject armor, String statName, String minValue, String maxValue){
		// Armor is not represented with its own class,
		// so we gotta create the attributes here
		
		if (statName.equals("armor_efficiency_kinetic")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_standard_protection.armor_eff_kinetic", randomValue);
		}
		
		if (statName.equals("armor_efficiency_energy")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_standard_protection.armor_eff_energy", randomValue);
		}
		
		if (statName.equals("special_protection_heat")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_special_protection.special_protection_type_heat", randomValue);
		}
		
		if (statName.equals("special_protection_cold")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_special_protection.special_protection_type_cold", randomValue);
		}
		
		if (statName.equals("special_protection_acid")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_special_protection.special_protection_type_acid", randomValue);
		}
		
		if (statName.equals("special_protection_electricity")){	
			int minimalValue = (int) Integer.parseInt(minValue);
			int maximalValue = (int) Integer.parseInt(maxValue);
			int randomValue  = minimalValue + new Random().nextInt(maximalValue-minimalValue);
			armor.setIntAttribute("cat_armor_special_protection.special_protection_type_electricity", randomValue);
		}
	}
	
	/*
	1377	wpn_category_0	Rifle
	1378	wpn_category_1	Carbine
	1379	wpn_category_10	Two Handed Lightsaber
	1380	wpn_category_11	Lightsaber Polearm
	1381	wpn_category_12	Free Targeting Heavy Weapon
	1382	wpn_category_13	Directional Heavy Weapon
	1383	wpn_category_2	Pistol
	1384	wpn_category_3	Heavy Weapon
	1385	wpn_category_4	One-Handed Melee
	1386	wpn_category_5	Two-Handed Melee
	1387	wpn_category_6	Unarmed
	1388	wpn_category_7	Polearm
	1389	wpn_category_8	Thrown
	1390	wpn_category_9	One Handed Lightsaber
	 */
}	
