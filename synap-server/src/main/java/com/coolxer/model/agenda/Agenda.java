package com.coolxer.model.agenda;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.ArrayList;

/**

 */
@Getter
@AllArgsConstructor
public class Agenda {
    private String tag;
    private String source;
    private AgendaLevelEnum level = AgendaLevelEnum.OTHER;

    public Agenda(String tag, String source) {
        this.tag = tag;
        this.source = source;
    }

    public ArrayList<Object> toArrayList(){
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(tag);
        arrayList.add(source);
        arrayList.add(level.getCode());
        return arrayList;
    }
}
