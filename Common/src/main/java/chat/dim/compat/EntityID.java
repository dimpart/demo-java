/* license: https://mit-license.org
 *
 *  Ming-Ke-Ming : Decentralized User Identity Authentication
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.compat;

import chat.dim.protocol.Address;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.type.ConstantString;

/**
 *  ID for entity (User/Group)
 *
 *      data format: "name@address[/terminal]"
 *
 *      fields:
 *          name     - entity name, the seed of fingerprint to build address
 *          address  - a string to identify an entity
 *          terminal - entity login resource(device), OPTIONAL
 */
final class EntityID extends ConstantString implements ID {

    private final String name;
    private final Address address;
    private final String terminal;

    public EntityID(String identifier, String name, Address address, String terminal) {
        super(identifier);
        this.name = name;
        this.address = address;
        this.terminal = terminal;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public String getTerminal() {
        return terminal;
    }

    /**
     *  Get Network ID
     *
     * @return address type as network ID
     */
    @Override
    public int getType() {
        String text = getName();
        if (text == null || text.isEmpty()) {
            // all ID without 'name' field must be a user
            // e.g.: BTC address
            return EntityType.USER.value;
        }
        assert address != null : "ID.address should not be empty: " + this;
        // compatible with MKM 0.9.*
        return NetworkID.getType(address.getNetwork());
    }

    @Override
    public boolean isBroadcast() {
        return EntityType.isBroadcast(getType());
    }

    @Override
    public boolean isUser() {
        return EntityType.isUser(getType());
    }

    @Override
    public boolean isGroup() {
        return EntityType.isGroup(getType());
    }

    @Override
    public boolean isSameAs(Object other) {
        ID did = ID.parse(other);
        if (did == null) {
            // should not happen
            return false;
        } else if (this == did) {
            // same object
            return true;
        }
        //
        //  1. check address
        //
        Address thisAddress = getAddress();
        Address thatAddress = did.getAddress();
        if (!thisAddress.equals(thatAddress)) {
            // addresses not equal,
            // sure not the same entity
            return false;
        }
        //
        //  2. check name
        //
        String thisName = getName();
        String thatName = did.getName();
        if (thisName == null || thisName.isEmpty()) {
            // names empty
            return thatName == null || thatName.isEmpty();
        } else if (thatName == null || thatName.isEmpty()) {
            // names not equal
            return false;
        }
        // both names are not empty,
        // check if they are equal
        return thisName.equals(thatName);
    }

    @Override
    public ID withoutTerminal() {
        // check old terminal (device)
        String device = getTerminal();
        if (device == null/* || device.isEmpty()*/) {
            // nothing changed
            return this;
        }
        // create new ID without terminal
        return ID.create(getName(), getAddress(), null);
    }

    @Override
    public ID withTerminal(String terminal) {
        // check old terminal (device)
        String device = getTerminal();
        if (terminal == null || terminal.isEmpty()) {
            // should not happen
            if (device == null || device.isEmpty()) {
                return this;
            } else {
                return ID.create(getName(), getAddress(), null);
            }
        }
        // new terminal not empty (normally),
        // try to add/replace terminal
        if (terminal.equals(device)) {
            // old terminal equals to the new terminal,
            // nothing changed
            return this;
        }
        // create new ID with terminal
        return ID.create(getName(), getAddress(), terminal);
    }

}
