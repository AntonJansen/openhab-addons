/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.lifx.internal.dto;

import java.nio.ByteBuffer;

import org.openhab.binding.lifx.internal.fields.ByteField;
import org.openhab.binding.lifx.internal.fields.Field;

/**
 * @author Tim Buckley - Initial contribution
 * @author Karel Goderis - Enhancement for the V2 LIFX Firmware and LAN Protocol Specification
 */
public class EchoRequestResponse extends Packet {

    public static final int TYPE = 0x3B;

    public static final Field<ByteBuffer> FIELD_PAYLOAD = new ByteField(64);

    private ByteBuffer payload;

    public EchoRequestResponse() {
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public void setPayload(ByteBuffer location) {
        this.payload = location;
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 64;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        payload = FIELD_PAYLOAD.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_PAYLOAD.bytes(payload));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }
}
