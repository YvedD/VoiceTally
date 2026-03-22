package com.yvesds.vt5.features.masterClient

import android.content.Context

/**
 * Compatibiliteitslaag bovenop de bestaande pairing-opslag.
 *
 * In fase 1 blijven de legacy hotspot-velden onderliggend bestaan, maar de rest van de code
 * kan al lezen/schrijven in termen van een neutrale pairing-transport context.
 */
object McPairingNetworkStore {

    fun read(context: Context): McPairingTransport {
        val passphrase = MasterClientPrefs.getHotspotPassword(context)
        return McPairingTransport(
            kind = MasterClientPrefs.getPairingTransportKind(context),
            networkName = MasterClientPrefs.getHotspotSsid(context),
            passphrase = passphrase,
            security = MasterClientPrefs.getHotspotSecurity(context).ifBlank {
                if (passphrase.isBlank()) "NOPASS" else "WPA"
            },
            sessionId = MasterClientPrefs.getPairingSessionId(context),
            ownerAddress = MasterClientPrefs.getGroupOwnerAddress(context),
            ownerDeviceAddress = MasterClientPrefs.getGroupOwnerDeviceAddress(context),
            ownerDeviceName = MasterClientPrefs.getGroupOwnerDeviceName(context),
            serviceTag = MasterClientPrefs.getPairingServiceTag(context)
        )
    }

    fun write(
        context: Context,
        transport: McTransportKind,
        networkName: String,
        passphrase: String,
        security: String,
        sessionId: String = "",
        ownerAddress: String = "",
        ownerDeviceAddress: String = "",
        ownerDeviceName: String = "",
        serviceTag: String = ""
    ) {
        MasterClientPrefs.setHotspotSsid(context, networkName)
        MasterClientPrefs.setHotspotPassword(context, passphrase)
        MasterClientPrefs.setHotspotSecurity(context, security)
        MasterClientPrefs.setPairingTransport(context, transport.wireValue)
        MasterClientPrefs.setPairingSessionId(context, sessionId)
        MasterClientPrefs.setGroupOwnerAddress(context, ownerAddress)
        MasterClientPrefs.setGroupOwnerDeviceAddress(context, ownerDeviceAddress)
        MasterClientPrefs.setGroupOwnerDeviceName(context, ownerDeviceName)
        MasterClientPrefs.setPairingServiceTag(context, serviceTag)
    }

    fun clear(context: Context) {
        MasterClientPrefs.clearHotspotCredentials(context)
        MasterClientPrefs.clearPairingTransportMetadata(context)
    }
}

