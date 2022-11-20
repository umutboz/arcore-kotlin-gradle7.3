package com.uboz.ar1

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.collision.Box
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.uboz.ar1.databinding.ActivityMainBinding
import com.uboz.ar1.models.Model
import com.uboz.ar1.models.ModelAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.CompletableFuture

private const val BOTTOM_SHEET_PEEK_HEIGHT = 50f
private const val DOUBLE_TAP_TOLERANCE_MS = 1000L
class MainActivity : AppCompatActivity() {

    private val models = mutableListOf(
        Model(R.drawable.chair, "Chair", R.raw.chair),
        Model(R.drawable.oven, "Oven", R.raw.oven),
        Model(R.drawable.piano, "Piano", R.raw.piano),
        Model(R.drawable.table, "Table", R.raw.table)
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectedModel: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //initialize our AR fragment
        arFragment = fragment as ArFragment
        setupBottomSheet()
        setupRecyclerView()
        setupDoubleTapArPlaneListener()

    }
    private fun setupBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheetBehavior.peekHeight =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                BOTTOM_SHEET_PEEK_HEIGHT,
                resources.displayMetrics
            ).toInt()

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                bottomSheet.bringToFront()
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {}
        })
    }
    private fun setupRecyclerView() {
        rvModels.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvModels.adapter = ModelAdapter(models)
        val adapter = rvModels.adapter as ModelAdapter
        // Selected Model Observe and set the selected model
        adapter.selectedModel.observe(this@MainActivity, Observer { model ->
                selectedModel = model
                val modelTitle = "Models (${model.title})"
                tvModel.text = modelTitle
            })

    }

    // using models ------------------------>

    private fun setupDoubleTapArPlaneListener() {

        var firstTapTime = 0L
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            // check selected model is initialized
            if (!this@MainActivity::selectedModel.isInitialized) {
                Toast.makeText(this@MainActivity, "Please select a model", Toast.LENGTH_SHORT).show()
            }else {
                if(firstTapTime == 0L) {
                    firstTapTime = System.currentTimeMillis()
                } else if(System.currentTimeMillis() - firstTapTime < DOUBLE_TAP_TOLERANCE_MS) {
                    firstTapTime = 0L
                    loadModel { modelRenderable, viewRenderable ->
                        addNodeToScene(hitResult.createAnchor(), modelRenderable, viewRenderable)
                    }
                } else {
                    firstTapTime = System.currentTimeMillis()
                }
            }
        }
    }


    lateinit var arFragment: ArFragment
    private fun getCurrentScene() = arFragment.arSceneView.scene

    private fun createDeleteButton(): Button {
        return Button(this).apply {
            this.text =  context.getString(R.string.delete)
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
        }
    }
    //Call requires API level 24 (current min is 21): builder
    private fun loadModel(callback: (ModelRenderable, ViewRenderable) -> Unit) {
        // selectedModel 3d model
        val modelRenderer = ModelRenderable.builder()
            .setSource(this, selectedModel.modelResourceId)
            .build()
        // selectedModel 3d model add Button
        val viewRenderer = ViewRenderable.builder()
            .setView(this, createDeleteButton())
            .build()
        CompletableFuture.allOf(modelRenderer, viewRenderer)
            .thenAccept {
                callback(modelRenderer.get(), viewRenderer.get())
            }
            .exceptionally {
                Toast.makeText(this, "Error loading model: $it", Toast.LENGTH_LONG).show()
                null
            }
    }

    private fun addNodeToScene(
        anchor: Anchor,
        modelRenderable: ModelRenderable,
        viewRenderable: ViewRenderable
    ) {
        val anchorNode = AnchorNode(anchor)
        val modelNode = TransformableNode(arFragment.transformationSystem).apply {
            renderable = modelRenderable
            setParent(anchorNode)
            getCurrentScene().addChild(anchorNode)
            select()
        }
        val viewNode = Node().apply {
            renderable = null
            setParent(modelNode)
            val box = modelNode.renderable?.collisionShape as Box
            localPosition = Vector3(0f, box.size.y, 0f)
            (viewRenderable.view as Button).setOnClickListener {
                getCurrentScene().removeChild(anchorNode)
            }
        }
        modelNode.setOnTapListener { _, _ ->
            if(!modelNode.isTransforming) {
                if(viewNode.renderable == null) {
                    viewNode.renderable = viewRenderable
                } else {
                    viewNode.renderable = null
                }
            }
        }
    }


}