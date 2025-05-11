//// En tu App de Prueba Mínima - TestHioPosActivity.kt
//class TestHioPosActivity : AppCompatActivity() {
//    private val PAYMENT_REQUEST_CODE = 123 // Elige un código
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // UI simple con un botón
//        val button = Button(this)
//        button.text = "Iniciar Pago Yappy"
//        button.setOnClickListener {
//            val paymentIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION")
//            paymentIntent.setComponent(ComponentName("com.example.tefbanesco", "com.example.tefbanesco.MainActivity"))
//            // Añadir extras mínimos necesarios por tu módulo
//            paymentIntent.putExtra("Amount", "100") // 1.00
//            paymentIntent.putExtra("TransactionId", 99999)
//            paymentIntent.putExtra("CurrencyISO", "590")
//            paymentIntent.putExtra("TransactionType", "SALE")
//            // ...otros extras que tu TransactionHandler espera...
//            try {
//                startActivityForResult(paymentIntent, PAYMENT_REQUEST_CODE)
//            } catch (e: Exception) {
//                Log.e("TestHioPos", "Error al lanzar Yappy: ${e.message}")
//                Toast.makeText(this, "Error al lanzar Yappy: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
//        setContentView(button)
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        Log.d("TestHioPos", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
//        if (requestCode == PAYMENT_REQUEST_CODE) {
//            if (resultCode == Activity.RESULT_OK) {
//                Log.d("TestHioPos", "Resultado OK recibido de Yappy.")
//                if (data != null && data.extras != null) {
//                    Log.d("TestHioPos", "Extras recibidos: ${com.example.tefbanesco.utils.bundleExtrasToString(data.extras!!)}")
//                    // Aquí puedes verificar campos específicos
//                    val transactionResult = data.getStringExtra("TransactionResult")
//                    Log.d("TestHioPos", "TransactionResult: $transactionResult")
//                    // ... y así para otros campos ...
//                } else {
//                    Log.w("TestHioPos", "Resultado OK pero sin datos (extras) de Yappy.")
//                }
//            } else if (resultCode == Activity.RESULT_CANCELED) {
//                Log.w("TestHioPos", "Yappy devolvió RESULT_CANCELED.")
//            } else {
//                Log.w("TestHioPos", "Yappy devolvió un resultCode inesperado: $resultCode")
//            }
//        } else {
//            Log.w("TestHioPos", "onActivityResult con requestCode desconocido: $requestCode")
//        }
//    }
//}