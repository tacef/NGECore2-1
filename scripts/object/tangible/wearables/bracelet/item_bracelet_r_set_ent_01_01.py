import sys

def setup(core, object):
	object.setStfFilename('static_item_n')
	object.setStfName('item_bracelet_r_set_ent_01_01')
	object.setDetailFilename('static_item_d')
	object.setDetailName('item_bracelet_r_set_ent_01_01')
	object.setIntAttribute('required_combat_level', 85)
	object.setStringAttribute('class_required', 'Entertainer')
	object.setIntAttribute('cat_stat_mod_bonus.@stat_n:strength_modified', 30)
	object.setIntAttribute('cat_stat_mod_bonus.@stat_n:agility_modified', 30)
	object.setStringAttribute('@set_bonus:piece_bonus_count_3', '@set_bonus:set_bonus_ent_1')
	object.setStringAttribute('@set_bonus:piece_bonus_count_4', '@set_bonus:set_bonus_ent_2')
	object.setStringAttribute('@set_bonus:piece_bonus_count_5', '@set_bonus:set_bonus_ent_3')
	object.setAttachment('setBonus', 'set_bonus_ent')
	return