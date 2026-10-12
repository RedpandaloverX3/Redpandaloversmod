package redpandaloversmod.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.minecraft.network.chat.Component
import net.minecraft.client.particle.SimpleAnimatedParticle
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import kotlin.math.sqrt
import com.google.gson.GsonBuilder
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import org.slf4j.LoggerFactory
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.resources.Identifier

var markerx: Int?=null
var markerz: Int?=null
var markerdim: Int?=null


data class portalmarker(
	val x: Int,
	val y: Int,
	val z: Int,
	val dimension: Int
)
var portalmarkernumber=1
var angletimer:Int?=null


data class Preset(
	val yaw: Float,
	val pitch: Float,
)
data class Settings(
	var angletimer:Int=40,
	var hotkey1: String? = null,
	var hotkey2: String? = null
)
var settings = Settings()

val presets = mutableMapOf<String, Preset>()
val portalmarkers = mutableMapOf<String, portalmarker>()

val category = KeyMapping.Category.register(
	Identifier.fromNamespaceAndPath(
		"redpandaloversmod",
		"custom"
	)
)
val hotkey1keybind = KeyMapping(
	"key.redpandaloversmod.1",
	0,
	category
)
val hotkey2keybind = KeyMapping(
	"key.redpandaloversmod.2",
	0,
	category
)



object RedpandaloversmodClient : ClientModInitializer {
	override fun onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(hotkey1keybind)
		KeyMappingHelper.registerKeyMapping(hotkey2keybind)

		val LOGGER = LoggerFactory.getLogger("Redpandaloversmod")
		loadSettings()
		loadPresets()
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("Redpandaloversmod_settings")
				.then(
					argument("setting", StringArgumentType.string())
						.suggests { _, builder ->
							builder.suggest("Angletimer")
							builder.suggest("Hotkey1")
							builder.suggest("Hotkey2")
							builder.buildFuture()
						}
						.then(
							argument("value", StringArgumentType.string())
						.executes { context ->
							val value = StringArgumentType.getString(context, "value")
							val settingtype = StringArgumentType.getString(context, "setting")
							if (settingtype =="Angletimer") {
								settings.angletimer = value.toInt()
								context.source.sendFeedback(Component.literal("Set Angletimer to $value"))
							}
							if (settingtype =="Hotkey1") {
								settings.hotkey1 = value
								context.source.sendFeedback(Component.literal("Set Hotkey1 to preset $value"))
							}
							if (settingtype =="Hotkey2") {
								settings.hotkey2 = value
								context.source.sendFeedback(Component.literal("Set Hotkey2 to preset $value"))
							}
							1
						})))
		}

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("copycoords")
					.executes { context ->

						val player = Minecraft.getInstance().player?:return@executes 0

						val x = player.blockX
						val z = player.blockZ
						Minecraft.getInstance().keyboardHandler.clipboard = "$x $z"
						context.source.sendFeedback(
							Component.literal("Copied $x $z to the clipboard")
						)
						1
					}
					.then(
						argument("", StringArgumentType.string())
							.suggests { _, builder ->
								builder.suggest("Include_Y")
								builder.suggest("Only_X")
								builder.suggest("Only_Z")

								builder.buildFuture()
							}
							.executes { context ->
								val text = StringArgumentType.getString(context, "")
								val player = Minecraft.getInstance().player
								val x = player?.blockX ?: 0
								val y = player?.blockY ?: 0
								val z = player?.blockZ ?: 0
								if (text=="Include_Y") {
									Minecraft.getInstance().keyboardHandler.clipboard = "$x $y $z"
									context.source.sendFeedback(
										Component.literal("Copied $x $y $z to the clipboard")
									)
								}
								if (text=="Only_X") {
									Minecraft.getInstance().keyboardHandler.clipboard = "$x"
									context.source.sendFeedback(
										Component.literal("Copied $x to the clipboard")
									)
								}
								if (text=="Only_Z") {
									Minecraft.getInstance().keyboardHandler.clipboard = "$z"
									context.source.sendFeedback(
										Component.literal("Copied $z to the clipboard")
									)
								}

									1

							}
					)
			)

		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("portalcoords")
					.executes { context ->
						val player = Minecraft.getInstance().player
						var x = player?.blockX ?: 0
						val y = player?.blockY ?: 0
						var z = player?.blockZ ?: 0
						val dimension = context.source.player.level().dimension().identifier()
						if (dimension.path == "the_nether") {
							x = x.times(8)
							z = z.times(8)
							portalmarkers[portalmarkernumber.toString()] = portalmarker(x, y, z, 0)
							portalmarkernumber++
							Minecraft.getInstance().keyboardHandler.clipboard = "$x $y $z"
							context.source.sendFeedback(
								Component.literal("Copied $x $y $z to the clipboard")
							)
						}
						if (dimension.path == "overworld") {
							x = x.div(8)
							z = z.div(8)
							portalmarkers[portalmarkernumber.toString()] = portalmarker(x, y, z, -1)
							portalmarkernumber++
							Minecraft.getInstance().keyboardHandler.clipboard = "$x $y $z"
							context.source.sendFeedback(
								Component.literal("Copied $x $y $z to the clipboard")
							)
						}
						1

					}
					.then(
						argument("x", IntegerArgumentType.integer())
							.then(
								argument("z", IntegerArgumentType.integer())
									.then(
										argument("Dimension", StringArgumentType.string())
											.suggests { _, builder ->
												builder.suggest("Nether")
												builder.suggest("Overworld")
												builder.buildFuture()
											}
											.executes { context ->
												val dimension = StringArgumentType.getString(context, "Dimension")
												var x = IntegerArgumentType.getInteger(context, "x")
												var z = IntegerArgumentType.getInteger(context, "z")
												if (dimension == "Nether") {
													x = x.times(8)
													z = z.times(8)
													portalmarkers[portalmarkernumber.toString()] = portalmarker(x, -100,z, 0)
													portalmarkernumber++
													Minecraft.getInstance().keyboardHandler.clipboard = "$x $z"
													context.source.sendFeedback(
														Component.literal("Copied $x $z to the clipboard")
													)
												}
												if (dimension == "Overworld") {
													x = x.div(8)
													z = z.div(8)
													portalmarkers[portalmarkernumber.toString()] = portalmarker(x, -100,z, -1)
													portalmarkernumber++
													Minecraft.getInstance().keyboardHandler.clipboard = "$x $z"
													context.source.sendFeedback(
														Component.literal("Copied $x $z to the clipboard")
													)
												}

												1
											})
							)
					)
			)
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("setmarker")
					.executes { context ->
						val player = Minecraft.getInstance().player?:return@executes 1
						val dimension = player.level().dimension().identifier()

						val x = player.blockX
						val z = player.blockZ
						markerx = x
						markerz = z
						val dimId = when (dimension.path) {
							"overworld" -> 0
							"the_nether" -> -1
							"the_end" -> 1
							else -> return@executes 1
						}
						val dimensionforfeedback=dimension.path
						markerdim = dimId
						context.source.sendFeedback(
							Component.literal("Set marker to $x $z in the $dimensionforfeedback")
						)

						1
					}.then(
						argument("x", IntegerArgumentType.integer())
							.then(
								argument("z", IntegerArgumentType.integer())
									.executes { context ->
										val x = IntegerArgumentType.getInteger(context, "x")
										val z = IntegerArgumentType.getInteger(context, "z")

										val player = Minecraft.getInstance().player?:return@executes 1
										val dimension = player.level().dimension().identifier()
										val dimId = when (dimension.path) {
											"overworld" -> 0
											"the_nether" -> -1
											"the_end" -> 1
											else -> return@executes 1
										}
										markerx = x
										markerz = z
										markerdim = dimId
										val dimensionforfeedback=dimension.path
										if (dimensionforfeedback == "overworld") {
											context.source.sendFeedback(Component.literal("Set marker to $x $z in the_$dimensionforfeedback"))
										}
										else{
											context.source.sendFeedback(Component.literal("Set marker to $x $z in $dimensionforfeedback"))
										}

										1
									})))
		}


		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("SetAnglePreset")
					.then(
						argument("Preset_Name", StringArgumentType.string())
							.then(
								argument("yaw", FloatArgumentType.floatArg())
									.then(
										argument("pitch", FloatArgumentType.floatArg())
											.executes { context ->
												val preset = StringArgumentType.getString(context,"Preset_Name")
												val yaw = FloatArgumentType.getFloat(context, "yaw")
												val pitch = FloatArgumentType.getFloat(context, "pitch")

												presets[preset] = Preset(
                                                    yaw = yaw,
                                                    pitch = pitch
                                                )

												1
											})
							)
					)
			)
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("AngleSnap")
					.then(
						argument("Preset_Name", StringArgumentType.string())
							.suggests { _, builder ->
								presets.keys.forEach { name ->
									builder.suggest(name)
								}
								builder.buildFuture()
							}
							.executes { context ->
								val preset = StringArgumentType.getString(context,"Preset_Name")
								val angles = presets[preset]

								val yaw = angles?.yaw
								val pitch = angles?.pitch

								val player = Minecraft.getInstance().player
								if (player != null) {
                                    if (yaw != null) {
                                        player.yRot = yaw
                                    }
                                    if (pitch != null) {
                                        player.xRot = pitch
                                    }
									angletimer=settings.angletimer
								}

								1
							}))
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("ClearMarkers")
					.executes { context ->

						 markerx=null
						 markerz=null
						 markerdim=null
						portalmarkers.clear()
						1
					}
					.then(
						argument("", StringArgumentType.string())
							.suggests { _, builder ->
								builder.suggest("OnlyPortalMarkers")
								builder.suggest("OnlyNormalMarkers")
								builder.buildFuture()
							}
							.executes { context ->
								val markertogetrifof = StringArgumentType.getString(context,"")
								if (markertogetrifof=="OnlyPortalMarkers"){
									portalmarkers.clear()
								}
								if (markertogetrifof=="OnlyNormalMarkers"){
									markerx=null
									markerz=null
									markerdim=null
								}

								1
							}))
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			val player = Minecraft.getInstance().player ?: return@register
			val dimension = player.level().dimension().identifier()
			val mc = Minecraft.getInstance()
			val level = mc.level ?: return@register

			val dimId = when (dimension.path) {
				"overworld" -> 0
				"the_nether" -> -1
				"the_end" -> 1
				else -> return@register
			}
			for ((_,portalmarker) in portalmarkers) {
				if (dimId == portalmarker.dimension) {
                    val x = portalmarker.x.toDouble() + 0.5
					val portaly = portalmarker.x.toDouble() + 0.5
                    val z = portalmarker.z.toDouble() + 0.5
                    val dx = player.x - x
                    val dz = player.z - z
                    val dist = sqrt(dx * dx + dz * dz)

                    val size = (dist / 15.0).coerceIn(1.0, 30.0).toFloat()

                    for (i in 0..((level.maxY - level.minY) * 2)) {
                        val y = level.minY + i * 0.5
                        val particle = mc.particleEngine.createParticle(
                            ParticleTypes.END_ROD,
                            x, y, z,
                            0.0, 0.0, 0.0
                        ) ?: continue
						particle.lifetime = 0
                        particle.scale(size)
						if (y== portaly) {
							(particle as? SimpleAnimatedParticle)?.let {
								it.setColor(0x7252f2)
								it.setFadeColor(0x7252f2)
							}
						}
                        (particle as? SimpleAnimatedParticle)?.let {
                            it.setColor(0x9C00FF)
                            it.setFadeColor(0x9C00FF)
                        }
                    }
                }
			}
			if (dimId==markerdim) {
				if (markerx != null && markerz != null) {
					val x = markerx!!.toDouble() + 0.5
					val z = markerz!!.toDouble() + 0.5

					val dx = player.x - x
					val dz = player.z - z
					val dist = sqrt(dx * dx + dz * dz)

					val size = (dist / 15.0).coerceIn(1.0, 30.0).toFloat()


					for (i in 0..((level.maxY - level.minY) * 2)) {
						val y = level.minY + i * 0.5
						val particle = mc.particleEngine.createParticle(
							ParticleTypes.END_ROD,
							x, y, z,
							0.0, 0.0, 0.0
						) ?: continue
						particle.scale(size)
						particle.lifetime=0
						(particle as? SimpleAnimatedParticle)?.let {
							it.setColor(0xFFFFFF)
							it.setFadeColor(0xFFFFFF)
						}
					}
				}
			}
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			val gson = GsonBuilder()
				.setPrettyPrinting()
				.create()
			val settingsfile = FabricLoader.getInstance()
				.configDir
				.resolve("Redpandaloversmod")
				.resolve("settings.json")
			Files.createDirectories(settingsfile.parent)

			Files.writeString(
				settingsfile,
				gson.toJson(settings)
			)

			val presetsFolder = FabricLoader.getInstance()
				.configDir
				.resolve("Redpandaloversmod")
				.resolve("presets")

			Files.createDirectories(presetsFolder)



			for ((name, preset) in presets) {
				val file = presetsFolder.resolve("$name.json")

				Files.writeString(
					file,
					gson.toJson(preset)
				)
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			if (angletimer==null) return@register
			if (angletimer!! <0){
				angletimer=null
				return@register
			}
			if (angletimer == 0){
				angletimer =null
				val player = Minecraft.getInstance().player
				if (player != null) {
					player.xRot = 180f
				}
			}
			else {
				angletimer = angletimer!! - 1
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			while (hotkey1keybind.consumeClick()) {
				hotkey(settings.hotkey1)
			}
			while (hotkey2keybind.consumeClick()) {
				hotkey(settings.hotkey2)
			}
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("RedpandaloversmodDebug")
					.executes { context ->
						context.source.sendFeedback(Component.literal("angletimer:${settings.angletimer}\nhotkey 1:${settings.hotkey1}\nhotkey 2:${settings.hotkey2}\nmarker:${markerx} ${markerz} ${markerdim}"))
						1
					})
		}
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
	}
}
fun loadSettings() {
	val gson = GsonBuilder()
		.setPrettyPrinting()
		.create()

	val settingsFile = FabricLoader.getInstance()
		.configDir
		.resolve("Redpandaloversmod")
		.resolve("settings.json")

	if (Files.exists(settingsFile)) {
		settings = gson.fromJson(
			Files.readString(settingsFile),
			Settings::class.java
		)
	}
}
fun loadPresets() {
	val gson = GsonBuilder()
		.setPrettyPrinting()
		.create()

	val presetsFolder = FabricLoader.getInstance()
		.configDir
		.resolve("Redpandaloversmod")
		.resolve("presets")

	if (!Files.exists(presetsFolder)) {
		return
	}

	Files.list(presetsFolder).use { files ->
		files.filter { Files.isRegularFile(it) }
			.filter { it.toString().endsWith(".json") }
			.forEach { file ->
				val name = file.fileName
					.toString()
					.removeSuffix(".json")

				val preset = gson.fromJson(
					Files.readString(file),
					Preset::class.java
				)

				presets[name] = preset
			}
	}
}
fun hotkey(presetname:String?) {
	if (presetname == null) return
	val angles = presets[presetname]

	val yaw = angles?.yaw
	val pitch = angles?.pitch

	val player = Minecraft.getInstance().player
	if (player != null) {
		if (yaw != null) {
			player.yRot = yaw
		}
		if (pitch != null) {
			player.xRot = pitch
		}
		angletimer=settings.angletimer
	}
}

