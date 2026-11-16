package net.createmod.ponder.authoring.practice;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Builder;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Goal;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/** Small virtual examples query current registered recipes instead of inventing products. */
public final class PracticeExamples {
    private PracticeExamples() {}
    public static PracticeDefinition create(String menuId) {
        Menu menu=Menu.fromId(menuId); Builder builder=PracticeDefinition.builder(menuId);
        if(menu==Menu.WORKBENCH) {
            builder.title("\u5de5\u4f5c\u53f0").slot(1,new ItemStack(Blocks.LOG,4)).duration(120);
            PracticeSession probe=new PracticeSession(builder.build(),PracticeMode.TRY);
            ItemStack output;
            try { output=probe.getSlot(0); } finally { probe.close(); }
            if(output.isEmpty()) return builder.title("\u5de5\u4f5c\u53f0\uff1a\u5f53\u524d\u914d\u65b9\u65e0\u6709\u6548\u4ea7\u7269").build();
            builder.action(click(40,0)).action(click(60,menu.slots));
            builder.goal(new Goal(Goal.Kind.SLOT_CONTAINS,menu.slots,output,"",0,"\u53d6\u51fa\u5f53\u524d\u914d\u65b9\u4ea7\u7269"));
        } else if(menu==Menu.FURNACE) {
            builder.title("\u7194\u7089").slot(0,new ItemStack(Blocks.IRON_ORE)).slot(1,new ItemStack(Items.COAL)).duration(240);
            ItemStack output=FurnaceRecipes.instance().getSmeltingResult(new ItemStack(Blocks.IRON_ORE));
            if(output.isEmpty()) return builder.title("\u7194\u7089\uff1a\u5f53\u524d\u914d\u65b9\u65e0\u6709\u6548\u4ea7\u7269").build();
            builder.goal(new Goal(Goal.Kind.SLOT_CONTAINS,2,output,"",0,"\u5b8c\u6210\u4e00\u6b21\u7194\u70bc"));
        } else if(menu==Menu.CHEST || menu==Menu.CHEST_LARGE) {
            ItemStack supply=new ItemStack(Items.IRON_INGOT,8);
            builder.title(menu==Menu.CHEST_LARGE ? "\u5927\u7bb1\u5b50" : "\u7bb1\u5b50").slot(menu.slots,supply).duration(100).action(click(20,menu.slots)).action(click(40,0));
            builder.goal(new Goal(Goal.Kind.SLOT_CONTAINS,0,supply,"",0,"\u5b58\u5165\u94c1\u952d"));
        } else if(menu==Menu.HOPPER) {
            ItemStack supply=new ItemStack(Items.IRON_INGOT,8);
            builder.title("\u6f0f\u6597").slot(0,supply).duration(100);
            builder.goal(new Goal(Goal.Kind.SLOT_CONTAINS,menu.slots,supply,"",0,"\u5c06\u7269\u54c1\u8f93\u9001\u81f3\u72ec\u7acb\u7f13\u51b2\u533a"));
        }
        return builder.build();
    }
    private static Action click(int tick,int slot) { return new Action(tick,Operation.CLICK_LEFT,slot,ItemStack.EMPTY,"",0); }
}
