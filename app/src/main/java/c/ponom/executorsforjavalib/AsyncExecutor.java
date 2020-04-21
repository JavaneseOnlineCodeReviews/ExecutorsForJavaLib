package c.ponom.executorsforjavalib;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@SuppressWarnings("WeakerAccess")
public abstract class AsyncExecutor {

     private long totalTasks;
     private long tasksCompleted=0;
     ThreadPoolExecutor currentExecutor=null;
     //ThreadPoolExecutor - тут используется именно он, поскольку  у него больше методов,
    // и он более управляем чем обычный Executors.newFixedThreadPool(n)


    /**
     *
     * <p> Метод исполняет переданный ему список Callable, в указанном числе потоков,  с вызовом
     * переданных в него слушателей на завершающие события исполнения потоков
     *
     * @param
     * numberOfThreads - число создаваемых потоков. Минимальное число -  1, для одного потока
     * обеспечивается последовательное исполнение переданных задач, для большего количества
     * порядок исполнения может быть любым.
     * @param
     * onCompletedListener,  -
     * @param
     * onEachCompletedListener,  -

     * @param
     * tasks - Runnable, их массив или список, передаваемый на исполнение
     * @param
     * activity - при передаче сюда активности, методы-слушатели вызываются в ее потоке,
     * при передаче null = в отдельном. Передача активности позволяет вызвать методы изменения ее ui
     * @return возвращает * ThreadPoolExecutor, у которого можно в любой момент  запросить
     * внутренними функциями, к примеру, данные о числе выполненных и выполняемых задач, или
     * сбросить все и остановить
     *
     * Exceptions - любые исключения, возникшие в процессе исполнения, возвращаются в onErrorListener,
     * если он установлен, иначе игнорируются.
     * таймауты исполнения потоков  в данный момент не устанавливаются и не используются
     * ВАЖНО:
     * экзекьютор нереентерабельный, второй submit работать не будет. Создавайте новый!
     */



    /*      todo - сделать набор юнит -тестов
       - реентерабельность - у нас пока одна очередь задач, хотите отправить несколько партий -
       заводите себе новый  под каждый раз, иначе бросит исключение.
       - работу на любом числе тредов и задач, от 1 до сотен
       - возвращаемые результаты - полученную коллекцию как будет готов возвращаемый тип,
       - (размер, адекватность содержимого)



    */
    ArrayList <Object> results=new ArrayList<>();

    public ThreadPoolExecutor submitTasks(int numberOfThreads,
                                          OnCompletedListener onCompletedListener,
                                          OnEachCompletedListener onEachCompletedListener,

                                          Activity activity,
                                          Callable... tasks)  {


        if (currentExecutor!=null&&currentExecutor.isTerminating())
            throw new IllegalStateException("This scheduler is in shutdown  mode," +
                " make a new instance");
        totalTasks=tasks.length;

        currentExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfThreads);

        for (int taskNumber=0;taskNumber<tasks.length; taskNumber++) {

            Callable boxedTask= boxTask( tasks[taskNumber],
                    taskNumber,
                    onCompletedListener,
                    onEachCompletedListener,
                    activity,
                    currentExecutor);
            currentExecutor.submit(boxedTask);

        }
    currentExecutor.shutdown();
    return currentExecutor;

    }



    /*
    удобный метод с сокращенным набором параметров, вызов только завершающего кода
    */
    public void submitTasks(int numberOfThreads,
                            OnCompletedListener onCompleted,
                            Activity activity,
                            Callable... tasks){

        submitTasks(numberOfThreads,
                onCompleted,
                null,
                activity,
                tasks);

    }




    /*удобный метод с минимальным набором параметров, ничего не вызываем*/
    public void submitTasks(int numberOfThreads, Activity activity,Callable... tasks){

        submitTasks(numberOfThreads,
                null,
                null,

                activity,
                tasks);
    }

    /*
    аналог упрощенного старого АсинкТаска. Ограничения и особенности:
    1. Принимается одна задача и используется один тред
    2. По завершении ее вызывается предоставленный коллбэк или ничего не вызывается если передан null.
    3. В его результатах будет объект с результатом (в т.ч. с полученным эксепшном при ошибке)
    4. Возвращается экзекьютор, с которым получатель может делать что хочет - к примеру отменить задание
    5. Коллбэк ПОКА (TODO) вызывается НЕ  ui потоке, поскольку мы ничего о нем не знаем
    6. Для получения сведений об исключении отправленный на вызов Callable должен иметь
    строку "return exception;" в catch секции. Исключение придет в коллбэк в качестве результата
   */



    public ThreadPoolExecutor asyncTask(@NonNull final Callable task,
                                        @Nullable final AsyncCallBack asyncCallBack,
                                        @Nullable final Activity activity) {

        if (currentExecutor != null && currentExecutor.isTerminating())
            throw new IllegalStateException("This scheduler is in shutdown  mode," +
                    " make a new instance");
        else  currentExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);


        final Callable boxedTask = new Callable() {
            @Override
            public Object call() {
                Object result = null;
                try {
                    result = task.call();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                /* когда мы попадаем сюда, у нас:
                В result будее результат кода, либо объект Exception если была ошибка.
                обработка ошибок - если получен Exception - он возвращается в качестве результата,
                 получатель сам анализирует тип результата
                */
                final Object finalResult = result;
                if (asyncCallBack != null) {
                    if (activity == null) {
                        asyncCallBack.asyncResult(finalResult);
                    } else {

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                asyncCallBack.asyncResult(finalResult);
                            }
                        });
                    }
                }
                return result;
            }
         };
        currentExecutor.submit(boxedTask);
        currentExecutor.shutdown();
        return currentExecutor;
    }


    /* Предельно упрощенный метод, принимающий только  Runnable/Callable и передающий их на
    исполнение в отдельном потоке. Результаты и ошибки не возвращаются.
    */

    public ThreadPoolExecutor asyncTaskSimple(@NonNull final Runnable task) {

        if (currentExecutor != null && currentExecutor.isTerminating())
            throw new IllegalStateException("This scheduler is in shutdown  mode," +
                    " make a new instance");
        else  currentExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);


        currentExecutor.submit(task);
        currentExecutor.shutdown();

        return currentExecutor;
    }


    public ThreadPoolExecutor asyncTaskSimple(@NonNull final Callable task) {

        if (currentExecutor != null && currentExecutor.isTerminating())
            throw new IllegalStateException("This scheduler is in shutdown  mode," +
                    " make a new instance");
        else  currentExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

        currentExecutor.submit(task);
        currentExecutor.shutdown();
        return currentExecutor;
    }







    /* метод  при необходимости обрамляет переданную задачу переданными  в него слушателями */
    private Callable boxTask(final Callable nextTask,
                             final int currentTaskNumber,
                             final OnCompletedListener onCompletedListener,
                             final OnEachCompletedListener onEachCompleted,

                             final Activity activity,
                             final ThreadPoolExecutor currentExecutor){


        //TODo - это крайне плохой стиль программирования, такое количество вложенных
        // if надо разбивать все это миимум на пять разных методов, кторые можно по
        // отдельности покрыть тестами

         final Callable boxedTask =new Callable() {
            @Override
            public Object call() {
                Object result = null;

                try{
                result = nextTask.call();

                }

                catch (  Exception exception) {
                    exception.printStackTrace();
                }
                /* когда мы попадаем сюда, у нас:
                В result будее результат кода, либо объект Exception если была ошибка.
                */

                // число выполненных задач считается с единицы, не с 0
                //обработка ошибок - если получен Exception - он возвращается в качестве результата,
                // получатель сам анализирует тип результата

                tasksCompleted++;
                final Object finalResult = result;
                results.add(result);
                if (onEachCompleted!=null) {
                    if (activity == null) {
                        onEachCompleted.runAfterEach(currentTaskNumber,finalResult ,
                                tasksCompleted,
                                totalTasks,
                                currentExecutor,
                                (float) tasksCompleted / (float) totalTasks * 100f);
                    } else {

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onEachCompleted.runAfterEach(currentTaskNumber, finalResult, tasksCompleted,
                                        totalTasks,
                                        currentExecutor,
                                        (float) tasksCompleted / (float) totalTasks * 100f);
                            }
                        });
                    }
                }
                    // обеспечение вызова завершающего кода

                    if (tasksCompleted<totalTasks) return null;
                    if(onCompletedListener==null)  return null;
                    else if (activity==null){
                        onCompletedListener.runAfterCompletion(results);
                        return null; }
                    else {activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onCompletedListener.runAfterCompletion(results);
                        }
                    });
                }
            return null;
            }


         };

        return boxedTask;
    }



    interface   AsyncCallBack{



            void asyncResult(Object result);

        }


    interface   OnCompletedListener{

        void runAfterCompletion(ArrayList<Object> results);

    }

    interface   OnEachCompletedListener{

        // переданные параметры могут быть использованы для оценки состояния исполнения
        void runAfterEach(long currentTaskNumber,
                          Object result, long tasksCompleted,
                          long totalTasks,
                          ThreadPoolExecutor currentExecutor,
                          float completion); // число выполненных задач от общего, в %
    }
    /*
    при переопредении данной функции следует внутри структуры catch (каждой) в конце выполнить
    return exception.

     */

}
