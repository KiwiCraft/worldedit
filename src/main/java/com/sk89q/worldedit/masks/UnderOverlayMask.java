// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit.masks;

import java.util.HashSet;
import java.util.Set;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BlockID;

/**
 *
 * @author 1337
 */
public class UnderOverlayMask implements Mask {

    boolean overlay;
    Set<Integer> ids = new HashSet<Integer>();

    public UnderOverlayMask(Set<Integer> ids, boolean overlay) {
        addAll(ids);
        this.overlay = overlay;
    }

    public void addAll(Set<Integer> ids) {
        this.ids.addAll(ids);
    }

    public boolean matches(EditSession editSession, Vector pos) {
        int id = editSession.getBlock(pos.setY(pos.getBlockY() + (overlay ? -1 : 1))).getType();
        return ids.isEmpty() ? id != BlockID.AIR : ids.contains(id);
    }

}
