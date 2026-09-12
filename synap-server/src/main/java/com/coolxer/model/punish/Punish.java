package com.coolxer.model.punish;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Objects;

/**

 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Punish {

    private String tag;
    private String sourceRegex;
    private Action action;

    public ArrayList<Object> toArrayList(){
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(tag);
        arrayList.add(sourceRegex);
        arrayList.add(action.toArrayList());
        return arrayList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Punish punish = (Punish) o;
        return Objects.equals(tag, punish.tag) &&
                Objects.equals(sourceRegex, punish.sourceRegex) &&
                Objects.equals( StringUtils.join(",",action.toArrayList()), StringUtils.join(",",punish.getAction().toArrayList()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, sourceRegex , StringUtils.join(",",action.toArrayList()));
    }

    @Data
    public static class Action{
        private int type;
        private String title;
        private String message;
        private String target;

        public ArrayList<Object> toArrayList() {
            ArrayList<Object> arrayList = new ArrayList<>();
            arrayList.add(type);
            arrayList.add(title);
            arrayList.add(message);
            arrayList.add(target);
            return arrayList;
        }
    }
}
