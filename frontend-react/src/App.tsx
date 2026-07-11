import { RouterProvider } from "react-router-dom"
import { useAuthBootstrap } from "@/hooks/useAuthBootstrap"
import { FullPageLoader } from "@/components/FullPageLoader"
import { router } from "@/router"

function App() {
  const isReady = useAuthBootstrap()

  if (!isReady) return <FullPageLoader />

  return <RouterProvider router={router} />
}

export default App
